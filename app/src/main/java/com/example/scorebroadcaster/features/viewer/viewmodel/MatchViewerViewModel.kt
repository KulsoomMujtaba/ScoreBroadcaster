package com.example.scorebroadcaster.features.viewer.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scorebroadcaster.features.match.data.Match
import com.example.scorebroadcaster.features.match.data.SupabaseMatchRepository
import com.example.scorebroadcaster.features.match.data.toMatch
import com.example.scorebroadcaster.features.scoring.data.MatchState
import com.example.scorebroadcaster.features.scoring.data.SupabaseEvent
import com.example.scorebroadcaster.features.scoring.data.SupabaseEventRepository
import com.example.scorebroadcaster.features.scoring.data.toBallEvent
import com.example.scorebroadcaster.features.scoring.domain.BallEvent
import com.example.scorebroadcaster.features.scoring.domain.reduce
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the read-only match viewer.
 *
 * Loads a published match and its events from Supabase using the share code, then
 * rebuilds the scoring state by replaying events through the existing [reduce] function.
 *
 * After a successful initial load, a Supabase Realtime subscription is started so that
 * new events are pushed instantly without polling.  Incoming events are:
 * - deduplicated by [SupabaseEvent.eventIndex]
 * - checked for ordering (out-of-order events are ignored for simplicity)
 * - applied through [reduce] to update [loadState] in place
 *
 * This ViewModel is intentionally read-only — it never mutates any Supabase data.
 * No scoring buttons, undo, or edit actions are exposed.
 *
 * Architecture note:
 * - [SupabaseMatchRepository.getMatchByShareCode] fetches match metadata.
 * - [SupabaseEventRepository.fetchMatchEvents] fetches the ordered event log.
 * - [SupabaseEventRepository.subscribeToMatchEvents] provides a Realtime INSERT feed.
 * - [reduce] (ScoreReducer) reconstructs the current state from the event log.
 * - UI simply collects [loadState] and renders it; no mutations are possible.
 */
class MatchViewerViewModel : ViewModel() {

    private val TAG = "MatchViewerVM"

    /** Represents the loading/error/success state of the viewer. */
    sealed class ViewerLoadState {
        object Idle : ViewerLoadState()
        object Loading : ViewerLoadState()
        data class Success(
            val match: Match,
            val firstInningsState: MatchState,
            val secondInningsState: MatchState?,
            val firstInningsEvents: List<BallEvent>,
            val secondInningsEvents: List<BallEvent>
        ) : ViewerLoadState()
        data class Error(val message: String) : ViewerLoadState()
    }

    private val _loadState = MutableStateFlow<ViewerLoadState>(ViewerLoadState.Idle)
    val loadState: StateFlow<ViewerLoadState> = _loadState.asStateFlow()

    // -------------------------------------------------------------------------
    // Realtime subscription state
    // -------------------------------------------------------------------------

    /** Channel used for the active Realtime subscription; null when not subscribed. */
    private var realtimeChannel: RealtimeChannel? = null

    /** Coroutine job collecting the Realtime flow; cancelling it stops event processing. */
    private var subscriptionJob: Job? = null

    /**
     * Set of global event indices that have already been applied locally.
     * Used to skip duplicate deliveries that may arrive via Realtime.
     */
    private val processedEventIndices = mutableSetOf<Int>()

    /**
     * The next global event index we expect from Realtime.
     * Events with a lower index are duplicates; events with a higher index are out-of-order
     * and are ignored (simple safety approach).
     */
    private var nextExpectedEventIndex = 0

    // -------------------------------------------------------------------------
    // Load
    // -------------------------------------------------------------------------

    /**
     * Fetch a published match by [shareCode] and rebuild its scoring state.
     *
     * On success, transitions to [ViewerLoadState.Success] and starts a Realtime
     * subscription so the state updates live as new events arrive.
     * On failure (invalid code, unpublished match, network error), transitions to [ViewerLoadState.Error].
     */
    fun loadMatchByShareCode(shareCode: String) {
        if (_loadState.value is ViewerLoadState.Loading) return
        _loadState.value = ViewerLoadState.Loading
        viewModelScope.launch {
            val normalised = shareCode.uppercase().trim()
            Log.d(TAG, "Fetching match by share code: $normalised")
            val supabaseMatch = SupabaseMatchRepository.getMatchByShareCode(normalised)
            if (supabaseMatch == null) {
                Log.d(TAG, "No match found for share code: $normalised")
                _loadState.value = ViewerLoadState.Error(
                    "No published match found for code \"$normalised\". " +
                    "Please check the code and try again."
                )
                return@launch
            }

            val match = supabaseMatch.toMatch()
            Log.d(TAG, "Fetching match events for ${match.id}")
            val remoteEvents = SupabaseEventRepository.fetchMatchEvents(match.id)

            if (remoteEvents.isEmpty()) {
                // Match found but no events yet — still valid; show zero score.
                Log.d(TAG, "Match found but has no events yet")
                initRealtimeTracking(eventCount = 0, loadedIndices = emptySet())
                _loadState.value = ViewerLoadState.Success(
                    match = match,
                    firstInningsState = MatchState(),
                    secondInningsState = null,
                    firstInningsEvents = emptyList(),
                    secondInningsEvents = emptyList()
                )
                startLiveUpdates(match.id)
                return@launch
            }

            val grouped = remoteEvents.groupBy { it.payload.inningsNumber }
            val firstEvents = grouped[1]
                ?.sortedBy { it.eventIndex }
                ?.map { it.toBallEvent() }
                ?: emptyList()
            val secondEvents = grouped[2]
                ?.sortedBy { it.eventIndex }
                ?.map { it.toBallEvent() }
                ?: emptyList()

            val firstState = reduce(firstEvents)
            val secondState = if (secondEvents.isNotEmpty()) reduce(secondEvents) else null

            Log.d(TAG, "Rebuilt state — 1st innings: ${firstState.runs}/${firstState.wickets}, " +
                "2nd innings: ${secondState?.runs}/${secondState?.wickets}")

            initRealtimeTracking(
                eventCount = remoteEvents.size,
                loadedIndices = remoteEvents.map { it.eventIndex }.toSet()
            )

            _loadState.value = ViewerLoadState.Success(
                match = match,
                firstInningsState = firstState,
                secondInningsState = secondState,
                firstInningsEvents = firstEvents,
                secondInningsEvents = secondEvents
            )
            startLiveUpdates(match.id)
        }
    }

    // -------------------------------------------------------------------------
    // Realtime
    // -------------------------------------------------------------------------

    /**
     * Initialise deduplication state from the events already loaded in bulk.
     *
     * [eventCount] determines the next expected sequential index, while
     * [loadedIndices] seeds the processed-indices set so that any Realtime
     * echo of an already-loaded event is silently dropped.
     */
    private fun initRealtimeTracking(eventCount: Int, loadedIndices: Set<Int>) {
        processedEventIndices.clear()
        processedEventIndices.addAll(loadedIndices)
        nextExpectedEventIndex = eventCount
    }

    /**
     * Start collecting Realtime INSERT events for [matchId].
     *
     * Cancels any previously active subscription before starting a new one.
     */
    private fun startLiveUpdates(matchId: String) {
        stopLiveUpdates()
        subscriptionJob = viewModelScope.launch {
            val result = SupabaseEventRepository.subscribeToMatchEvents(matchId)
            if (result == null) {
                Log.w(TAG, "Realtime subscription unavailable for match $matchId")
                return@launch
            }
            val (channel, eventFlow) = result
            realtimeChannel = channel
            eventFlow.collect { event ->
                handleRealtimeEvent(event)
            }
        }
    }

    /**
     * Process a single incoming Realtime event.
     *
     * - Skips duplicates (already seen [SupabaseEvent.eventIndex]).
     * - Skips out-of-order events (index ≠ [nextExpectedEventIndex]).
     * - Appends valid events to the in-memory list, re-reduces the innings state,
     *   and emits a new [ViewerLoadState.Success] so the UI updates instantly.
     */
    private fun handleRealtimeEvent(event: SupabaseEvent) {
        if (processedEventIndices.contains(event.eventIndex)) {
            Log.d(TAG, "Ignoring duplicate event index ${event.eventIndex}")
            return
        }
        if (event.eventIndex != nextExpectedEventIndex) {
            Log.w(TAG, "Out-of-order event index ${event.eventIndex}, expected $nextExpectedEventIndex — ignoring")
            return
        }
        processedEventIndices.add(event.eventIndex)
        nextExpectedEventIndex++

        val currentState = _loadState.value as? ViewerLoadState.Success ?: return
        val ballEvent = event.toBallEvent()
        val inningsNumber = event.payload.inningsNumber

        val newFirstEvents: List<BallEvent>
        val newSecondEvents: List<BallEvent>
        if (inningsNumber == 1) {
            newFirstEvents = currentState.firstInningsEvents + ballEvent
            newSecondEvents = currentState.secondInningsEvents
        } else {
            newFirstEvents = currentState.firstInningsEvents
            newSecondEvents = currentState.secondInningsEvents + ballEvent
        }

        val newFirstState = reduce(newFirstEvents)
        val newSecondState = if (newSecondEvents.isNotEmpty()) reduce(newSecondEvents) else null

        Log.d(TAG, "Live update — event index ${event.eventIndex}, innings $inningsNumber: " +
            "1st ${newFirstState.runs}/${newFirstState.wickets}, " +
            "2nd ${newSecondState?.runs}/${newSecondState?.wickets}")

        _loadState.value = currentState.copy(
            firstInningsState = newFirstState,
            secondInningsState = newSecondState,
            firstInningsEvents = newFirstEvents,
            secondInningsEvents = newSecondEvents
        )
    }

    /**
     * Stop the active Realtime subscription and clean up the channel.
     *
     * Safe to call multiple times; no-ops when there is no active subscription.
     * Called from [MatchViewerScreen] via a [androidx.compose.runtime.DisposableEffect]
     * when the screen leaves the composition, and also from [onCleared] as a safety net.
     */
    fun stopLiveUpdates() {
        subscriptionJob?.cancel()
        subscriptionJob = null
        val channel = realtimeChannel
        realtimeChannel = null
        if (channel != null) {
            viewModelScope.launch {
                runCatching { channel.unsubscribe() }
                    .onSuccess { Log.d(TAG, "Unsubscribed from realtime channel") }
                    .onFailure { e -> Log.e(TAG, "Error unsubscribing from realtime: ${e.message}") }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /** Reset to idle so the screen can be reused for a different share code. */
    fun reset() {
        stopLiveUpdates()
        processedEventIndices.clear()
        nextExpectedEventIndex = 0
        _loadState.value = ViewerLoadState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        stopLiveUpdates()
    }
}
