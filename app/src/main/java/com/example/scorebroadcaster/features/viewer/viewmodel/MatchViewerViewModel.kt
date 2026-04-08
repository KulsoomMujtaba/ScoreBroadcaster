package com.example.scorebroadcaster.features.viewer.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scorebroadcaster.features.match.data.Match
import com.example.scorebroadcaster.features.match.data.SupabaseMatchRepository
import com.example.scorebroadcaster.features.match.data.toMatch
import com.example.scorebroadcaster.features.scoring.data.MatchState
import com.example.scorebroadcaster.features.scoring.data.SupabaseEventRepository
import com.example.scorebroadcaster.features.scoring.data.toBallEvent
import com.example.scorebroadcaster.features.scoring.domain.BallEvent
import com.example.scorebroadcaster.features.scoring.domain.reduce
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
 * This ViewModel is intentionally read-only — it never mutates any Supabase data.
 * No scoring buttons, undo, or edit actions are exposed.
 *
 * Architecture note:
 * - [SupabaseMatchRepository.getMatchByShareCode] fetches match metadata.
 * - [SupabaseEventRepository.fetchMatchEvents] fetches the ordered event log.
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

    /**
     * Fetch a published match by [shareCode] and rebuild its scoring state.
     *
     * On success, transitions to [ViewerLoadState.Success].
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
                _loadState.value = ViewerLoadState.Success(
                    match = match,
                    firstInningsState = MatchState(),
                    secondInningsState = null,
                    firstInningsEvents = emptyList(),
                    secondInningsEvents = emptyList()
                )
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

            _loadState.value = ViewerLoadState.Success(
                match = match,
                firstInningsState = firstState,
                secondInningsState = secondState,
                firstInningsEvents = firstEvents,
                secondInningsEvents = secondEvents
            )
        }
    }

    /** Reset to idle so the screen can be reused for a different share code. */
    fun reset() {
        _loadState.value = ViewerLoadState.Idle
    }
}
