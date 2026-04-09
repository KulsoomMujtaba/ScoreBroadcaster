package com.example.scorebroadcaster.features.scoring.viewmodel
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scorebroadcaster.features.scoring.data.InningsPhase
import com.example.scorebroadcaster.features.scoring.data.MatchState
import com.example.scorebroadcaster.features.scoring.data.PendingAction
import com.example.scorebroadcaster.features.scoring.data.ScoreEvent
import com.example.scorebroadcaster.features.scoring.data.ScoringConsoleState
import com.example.scorebroadcaster.features.scoring.data.BattingEntry
import com.example.scorebroadcaster.features.scoring.data.BowlingEntry
import com.example.scorebroadcaster.features.scoring.data.FallOfWicket
import com.example.scorebroadcaster.features.match.data.Match
import com.example.scorebroadcaster.features.match.data.MatchStatus
import com.example.scorebroadcaster.features.scoring.data.Partnership
import com.example.scorebroadcaster.features.players.data.Player
import com.example.scorebroadcaster.features.teams.data.Team
import com.example.scorebroadcaster.features.scoring.data.toBallEvent
import com.example.scorebroadcaster.features.scoring.domain.BallEvent
import com.example.scorebroadcaster.features.scoring.domain.MaidenOverCalculator
import com.example.scorebroadcaster.features.scoring.domain.OverSummaryCalculator
import com.example.scorebroadcaster.features.scoring.domain.canAddPlayerToTeam
import com.example.scorebroadcaster.features.scoring.domain.getEligiblePlayersForTeam
import com.example.scorebroadcaster.features.scoring.domain.reduce
import com.example.scorebroadcaster.features.scoring.data.MatchRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.scorebroadcaster.features.scoring.data.Innings

class MatchViewModel : ViewModel() {

    /** The match entity currently being scored. Null until [initFromMatch] is called. */
    private val _activeMatch = MutableStateFlow<Match?>(null)
    val activeMatch: StateFlow<Match?> = _activeMatch.asStateFlow()

    // Internal event log uses BallEvent for flexible delivery modelling.
    private val _events = MutableStateFlow<List<BallEvent>>(emptyList())

    /** Current-innings event log exposed for the ball timeline / over history screen. */
    val events: StateFlow<List<BallEvent>> = _events.asStateFlow()

    /**
     * First-innings event log.
     * Populated when the first innings ends; stays empty until then.
     * After the second innings starts [events] tracks the second innings only.
     */
    private val _firstInningsEvents = MutableStateFlow<List<BallEvent>>(emptyList())
    val firstInningsEvents: StateFlow<List<BallEvent>> = _firstInningsEvents.asStateFlow()

    private val _state = MutableStateFlow(MatchState())
    val state: StateFlow<MatchState> = _state.asStateFlow()

    private val _consoleState = MutableStateFlow(ScoringConsoleState())
    val consoleState: StateFlow<ScoringConsoleState> = _consoleState.asStateFlow()

    /** Fall-of-wickets list for the current innings, in chronological order. */
    private val _fallOfWickets = MutableStateFlow<List<FallOfWicket>>(emptyList())
    val fallOfWickets: StateFlow<List<FallOfWicket>> = _fallOfWickets.asStateFlow()

    /** The live batting partnership currently in progress. Null before openers are set. */
    private val _currentPartnership = MutableStateFlow<Partnership?>(null)
    val currentPartnership: StateFlow<Partnership?> = _currentPartnership.asStateFlow()

    /** Completed partnerships for the current innings, in chronological order. */
    private val _completedPartnerships = MutableStateFlow<List<Partnership>>(emptyList())
    val completedPartnerships: StateFlow<List<Partnership>> = _completedPartnerships.asStateFlow()

    /**
     * Snapshot of the opener assignments made when [setOpeners] was called.
     * Keyed by innings number (1 or 2). Preserved across undo so that
     * [rebuildConsoleFromEvents] can restore the correct striker/non-striker/bowler
     * when the event list is emptied by undoing the first ball.
     */
    private data class InningsOpenersSnapshot(
        val striker: Player,
        val nonStriker: Player,
        val bowler: Player
    )
    private val inningsOpenersSnapshot = mutableMapOf<Int, InningsOpenersSnapshot>()

    /** One-shot message emitted after a successful undo. Cleared by [clearUndoMessage]. */
    private val _undoMessage = MutableStateFlow<String?>(null)
    val undoMessage: StateFlow<String?> = _undoMessage.asStateFlow()

    fun clearUndoMessage() { _undoMessage.value = null }

    /**
     * One-shot validation error emitted when a player cannot be added because they already
     * belong to the opposing team. Cleared by [clearValidationError].
     *
     * The UI should observe this flow and display the message via a Snackbar or Toast.
     */
    private val _validationError = MutableStateFlow<String?>(null)
    val validationError: StateFlow<String?> = _validationError.asStateFlow()

    fun clearValidationError() { _validationError.value = null }

    /**
     * Completed partnerships from the first innings.
     * Populated when the first innings ends; stays empty until then.
     */
    private val _firstInningsCompletedPartnerships = MutableStateFlow<List<Partnership>>(emptyList())
    val firstInningsCompletedPartnerships: StateFlow<List<Partnership>> = _firstInningsCompletedPartnerships.asStateFlow()

    // Preserved team names so they survive repeated reduce() calls
    private var currentTeamAName = "Team A"
    private var currentTeamBName = "Team B"

    // Job reference for in-flight state-resumption coroutine; cancelled on re-init.
    private var resumeJob: Job? = null

    /**
     * True while [initFromMatch] is asynchronously loading and rebuilding persisted match
     * state from the database/remote.  The scoring UI observes this to suppress the
     * innings-setup popup during the transient loading window so it is never shown
     * incorrectly for an already-started match.
     */
    private val _isResuming = MutableStateFlow(false)
    val isResuming: StateFlow<Boolean> = _isResuming.asStateFlow()

    // Mutex serialises ball-event persistence writes so rapid mutations (add/undo)
    // never produce a stale database row order.
    private val persistMutex = Mutex()

    // ---------------------------------------------------------------------------
    // Core scoring
    // ---------------------------------------------------------------------------

    /**
     * Record a [ScoreEvent] from the UI.
     *
     * The event is converted to a [BallEvent] before being appended to the internal log.
     * This keeps the UI layer stable while the scoring engine operates on the richer model.
     */
    fun addEvent(event: ScoreEvent) {
        addBallEvent(event.toBallEvent())
    }

    /**
     * Record a fully-constructed [BallEvent] directly.
     *
     * Use this for deliveries that cannot be expressed as a single [ScoreEvent], such as
     * extras with a wicket (e.g. run-out on a wide or no-ball).
     */
    fun addBallEvent(ballEvent: BallEvent) {
        if (ballEvent.wicket) {
            Log.d("WicketFlow", "Wicket recorded: ${ballEvent.dismissalDetail?.dismissalType} — ${ballEvent.dismissalDetail?.batter?.name}")
        }
        val console = _consoleState.value
        // Stamp the current bowler onto the event so MaidenOverCalculator can derive maiden
        // counts from the event log without needing any external player-tracking state.
        // Also stamp the current striker and non-striker so the live batting state can be
        // restored after an app restart without re-opening the innings-setup dialog.
        val stamped = ballEvent.copy(
            bowler = ballEvent.bowler ?: console.currentBowler,
            striker = ballEvent.striker ?: console.striker,
            nonStriker = ballEvent.nonStriker ?: console.nonStriker
        )
        val prevState = _state.value
        _events.value = _events.value + stamped
        val newState = reduce(_events.value)
            .copy(teamAName = currentTeamAName, teamBName = currentTeamBName)
        _state.value = newState
        if (stamped.wicket) {
            _fallOfWickets.value = computeFallOfWickets(_events.value)
        }
        updateConsoleAfterEvent(stamped, prevState, newState)
        if (stamped.wicket) {
            _consoleState.value = _consoleState.value.copy(
                currentInningsFallOfWickets = _fallOfWickets.value
            )
        }
        refreshCurrentInningsOverSummaries()
        persistCurrentInningsEvents()

        // Transition match status from NOT_STARTED → IN_PROGRESS on the first recorded ball.
        // The NOT_STARTED guard ensures this transition fires exactly once.
        val currentMatch = _activeMatch.value
        if (currentMatch != null && currentMatch.status == MatchStatus.NOT_STARTED) {
            Log.d("MatchViewModel", "First ball recorded — transitioning match ${currentMatch.localId} to IN_PROGRESS")
            val updated = currentMatch.copy(status = MatchStatus.IN_PROGRESS)
            _activeMatch.value = updated
            MatchRepository.updateMatch(updated)
        }

        // Async remote insert — must come after local append so sequenceNumber is accurate.
        // Does not block scoring; failures are logged and retried on next sync.
        _activeMatch.value?.localId?.let { matchId ->
            val inningsNumber = _consoleState.value.inningsNumber
            val sequenceNumber = _events.value.size - 1
            val globalIndex = if (inningsNumber == 1) sequenceNumber
                              else _firstInningsEvents.value.size + sequenceNumber
            Log.d("MatchViewModel", "Inserting event index $globalIndex (innings=$inningsNumber, seq=$sequenceNumber)")
            MatchRepository.insertRemoteEvent(matchId, inningsNumber, sequenceNumber, globalIndex, stamped)
        }
    }

    private fun updateConsoleAfterEvent(
        event: BallEvent,
        prevState: MatchState,
        newState: MatchState
    ) {
        val console = _consoleState.value
        if (console.phase == InningsPhase.SETUP || console.phase == InningsPhase.MATCH_COMPLETE) return
        val striker = console.striker ?: return

        val isWide = event.extras.wides > 0
        val isNoBall = event.extras.noBalls > 0
        val isBye = event.extras.byes > 0
        val isLegBye = event.extras.legByes > 0

        // --- Update striker batting entry ---
        // For a wicket: determine if the striker or non-striker was dismissed.
        val strikerIsOut = event.wicket &&
                event.dismissalDetail?.batter?.id == striker.id

        val updatedStrikerEntry = console.strikerEntry?.let { entry ->
            when {
                isWide -> entry // Wide: batter did not face the ball
                event.wicket && strikerIsOut ->
                    // Striker is out — increment balls, mark dismissed
                    entry.copy(balls = entry.balls + 1, isOut = true, dismissal = event.dismissalDetail)
                event.wicket ->
                    // Non-striker is out on a run out — striker still faced the ball
                    entry.copy(balls = entry.balls + 1)
                isNoBall ->
                    // No-ball: batter gets credit for runs but ball does not count
                    entry.copy(
                        runs = entry.runs + event.runsOffBat,
                        fours = if (event.runsOffBat == 4) entry.fours + 1 else entry.fours,
                        sixes = if (event.runsOffBat == 6) entry.sixes + 1 else entry.sixes
                    )
                isBye || isLegBye ->
                    // Bye / leg-bye: ball counts but no runs credited to batter
                    entry.copy(balls = entry.balls + 1)
                else ->
                    // Regular delivery: runs and ball count both credited
                    entry.copy(
                        runs = entry.runs + event.runsOffBat,
                        balls = entry.balls + 1,
                        fours = if (event.runsOffBat == 4) entry.fours + 1 else entry.fours,
                        sixes = if (event.runsOffBat == 6) entry.sixes + 1 else entry.sixes
                    )
            }
        }

        // --- Update non-striker batting entry when non-striker is out (run out) ---
        // Only computed (non-null) when the non-striker was actually dismissed.
        val updatedNonStrikerEntry: BattingEntry? = if (event.wicket && !strikerIsOut) {
            console.nonStrikerEntry?.copy(isOut = true, dismissal = event.dismissalDetail)
        } else null

        // --- Update current bowler entry ---
        val updatedBowlerEntry = console.currentBowlerEntry?.let { entry ->
            when {
                isWide ->
                    // Wide: charged to bowler; no ball count increment
                    entry.copy(runs = entry.runs + event.extras.wides)
                isNoBall ->
                    // No-ball: 1-run penalty + any runs off bat charged to bowler; no ball count
                    entry.copy(runs = entry.runs + event.extras.noBalls + event.runsOffBat)
                event.wicket -> {
                    val (o, b) = incrementBall(entry.overs, entry.balls)
                    // Only credit the bowler with a wicket if the dismissal type warrants it
                    if (event.dismissalDetail?.bowlerCredited == true) {
                        entry.copy(wickets = entry.wickets + 1, overs = o, balls = b)
                    } else {
                        entry.copy(overs = o, balls = b)
                    }
                }
                isBye || isLegBye -> {
                    val (o, b) = incrementBall(entry.overs, entry.balls)
                    entry.copy(overs = o, balls = b) // extras do not count against bowler
                }
                else -> {
                    val (o, b) = incrementBall(entry.overs, entry.balls)
                    entry.copy(runs = entry.runs + event.runsOffBat, overs = o, balls = b)
                }
            }
        }

        // --- Propagate to aggregate lists ---
        // Update the striker entry, then also update the non-striker entry if they were run out.
        val updatedAllBatting = run {
            val withStriker = if (updatedStrikerEntry != null) {
                console.allBattingEntries.map {
                    if (it.player.id == striker.id) updatedStrikerEntry else it
                }
            } else console.allBattingEntries

            if (updatedNonStrikerEntry != null && console.nonStriker != null) {
                withStriker.map {
                    if (it.player.id == console.nonStriker.id) updatedNonStrikerEntry else it
                }
            } else withStriker
        }

        val updatedAllBowling = if (updatedBowlerEntry != null && console.currentBowler != null) {
            console.allBowlingEntries.map {
                if (it.player.id == console.currentBowler.id) updatedBowlerEntry else it
            }
        } else console.allBowlingEntries

        // --- Detect over end (a legal ball that completes an over) ---
        val overEnded = event.countsAsBall && newState.balls == 0 && newState.overs > prevState.overs

        val wicketFell = event.wicket

        // --- Strike rotation ---
        // Runs from bat and byes/leg-byes can rotate the strike; wides and no-balls do not.
        val oddRuns = when {
            isWide || isNoBall -> false
            else -> (event.runsOffBat + event.extras.byes + event.extras.legByes) % 2 == 1
        }
        // Strike rotation — three sequential steps applied in order:
        //
        //  1. Run-crossing rotation: odd runs on a legal delivery mean the batters crossed
        //     during the run, so the non-striker is now at the batting end and vice versa.
        //
        //  2. Over-end rotation: at the end of an over the bowling direction reverses, so
        //     the two ends swap roles.  When combined with step 1, even runs on the last ball
        //     produce a net swap while odd runs on the last ball cancel out (no net change).
        //
        //  3. Wicket: null out whichever final position the dismissed batter occupies after
        //     steps 1 and 2.  This correctly handles wickets on the last ball of an over —
        //     the incoming batter fills the position left vacant after both rotations are applied.

        // Step 1: run-crossing rotation
        val (crossedStriker, crossedNonStriker) = if (oddRuns) {
            Pair(console.nonStriker, console.striker)
        } else {
            Pair(console.striker, console.nonStriker)
        }

        // Step 2: over-end rotation
        val (finalStriker, finalNonStriker) = if (overEnded) {
            Pair(crossedNonStriker, crossedStriker)
        } else {
            Pair(crossedStriker, crossedNonStriker)
        }

        // Step 3: wicket — null out the dismissed batter's final position
        val dismissedId: String? = when {
            !wicketFell -> null
            strikerIsOut -> striker.id
            else -> console.nonStriker?.id
        }
        val (rotatedStriker, rotatedNonStriker) = when {
            dismissedId == null -> Pair(finalStriker, finalNonStriker)
            finalStriker?.id == dismissedId -> Pair(null, finalNonStriker)
            finalNonStriker?.id == dismissedId -> Pair(finalStriker, null)
            else -> Pair(finalStriker, finalNonStriker)
        }

        // --- Determine pending action ---
        // When a wicket fell, the incoming batter's role is determined by whichever position
        // is null after all rotations — not simply by which batter was dismissed at delivery time.
        val incomingIsStriker = rotatedStriker == null
        val (pendingAction, bowlerChangePending) = when {
            wicketFell -> {
                // All out when 10 wickets have fallen (only 1 batter left – can't form a partnership).
                val allOut = newState.wickets >= 10
                if (!allOut) {
                    val remaining = eligibleNextBatters()
                    Log.d("WicketFlow", "pendingAction set to SelectNextBatter (${remaining.size} eligible team players, replacingStriker=$incomingIsStriker)")
                    Pair(PendingAction.SelectNextBatter(remaining, replacingStriker = incomingIsStriker), overEnded)
                } else {
                    // All out — no pending action; innings ends naturally
                    Pair(null, false)
                }
            }
            overEnded -> Pair(PendingAction.SelectBowler(availableBowlers()), false)
            else -> Pair(null, false)
        }

        // --- Derive maiden counts from the full event log and apply to bowling entries ---
        // This is computed from the event log (pure, replay-safe) rather than stored as a
        // mutable counter, ensuring correctness after undo, ball edits, and ball deletes.
        val maidensMap = MaidenOverCalculator.compute(_events.value)
        val bowlingWithMaidens = updatedAllBowling.map { entry ->
            entry.copy(maidens = maidensMap[entry.player.id] ?: 0)
        }
        val bowlerEntryWithMaidens = updatedBowlerEntry?.copy(
            maidens = maidensMap[console.currentBowler?.id] ?: 0
        )

        // --- Partnership tracking ---
        val partnershipRuns = event.runsOffBat + event.extras.total
        val partnershipBalls = if (event.countsAsBall) 1 else 0
        val newPartnershipRuns = if (wicketFell) 0 else console.currentPartnershipRuns + partnershipRuns
        val newPartnershipBalls = if (wicketFell) 0 else console.currentPartnershipBalls + partnershipBalls

        _consoleState.value = console.copy(
            striker = rotatedStriker,
            nonStriker = rotatedNonStriker,
            strikerEntry = updatedAllBatting.find { it.player.id == rotatedStriker?.id },
            nonStrikerEntry = updatedAllBatting.find { it.player.id == rotatedNonStriker?.id },
            currentBowlerEntry = bowlerEntryWithMaidens,
            allBattingEntries = updatedAllBatting,
            allBowlingEntries = bowlingWithMaidens,
            pendingAction = pendingAction,
            bowlerChangePending = bowlerChangePending,
            currentPartnershipRuns = newPartnershipRuns,
            currentPartnershipBalls = newPartnershipBalls
        )

        // --- Update current partnership ---
        val updatedPartnership = _currentPartnership.value?.let { p ->
            p.copy(
                runs = p.runs + partnershipRuns,
                balls = p.balls + partnershipBalls
            )
        }
        if (wicketFell) {
            // Finalize the current partnership and move it to completed list.
            updatedPartnership?.let { p ->
                _completedPartnerships.value = _completedPartnerships.value +
                        p.copy(endScore = newState.runs, isCurrent = false)
            }
            _currentPartnership.value = null
        } else {
            _currentPartnership.value = updatedPartnership
        }

        // --- Win condition: chasing team reaches or exceeds the target ---
        // Check after all state updates so the result banner reflects the final delivery.
        if (console.phase == InningsPhase.SECOND_INNINGS &&
            console.target > 0 &&
            newState.runs >= console.target
        ) {
            endMatch()
        }
    }

    fun undo() {
        val currentEvents = _events.value
        if (currentEvents.isEmpty()) return

        val inningsNumber = _consoleState.value.inningsNumber
        val targetIndex = currentEvents.size - 1
        Log.d("MatchViewModel", "Undo triggered → target index $targetIndex")

        // Compute the global index that the UNDO event itself will occupy.
        // It is the next position in the match-level event log (one past the last BALL event).
        val undoGlobalIndex = if (inningsNumber == 1) currentEvents.size
                              else _firstInningsEvents.value.size + currentEvents.size

        _events.value = currentEvents.dropLast(1)
        _state.value = reduce(_events.value)
            .copy(teamAName = currentTeamAName, teamBName = currentTeamBName)
        _fallOfWickets.value = computeFallOfWickets(_events.value)
        // Fully rebuild all per-player stats and batter positions from the remaining
        // events so that batting entries, bowling entries, and striker/non-striker
        // assignments are all consistent with the updated event log.
        rebuildConsoleFromEvents(_events.value)
        refreshCurrentInningsOverSummaries()
        persistCurrentInningsEvents()
        _undoMessage.value = "Last ball undone"

        // Append an UNDO_TO_INDEX event to the remote log so the undo is reflected on
        // all connected devices (viewers, other scorers) without deleting any rows.
        _activeMatch.value?.localId?.let { matchId ->
            MatchRepository.insertRemoteUndoEvent(matchId, inningsNumber, targetIndex, undoGlobalIndex)
        }
    }

    /**
     * Replace the [BallEvent] at [globalIndex] in the active-innings event log
     * (or the first-innings log when [inFirstInnings] is true) with [updatedEvent].
     *
     * The innings aggregate [MatchState] is rebuilt by replaying the modified event log.
     * Per-player batting/bowling stats in [ScoringConsoleState] are **not** rebuilt
     * (the same simplification applied by [undo]).
     *
     * @param globalIndex   0-based position in the target event log.
     * @param updatedEvent  The corrected [BallEvent] to store at that position.
     * @param inFirstInnings True when editing the archived first-innings log.
     */
    fun replaceBallEvent(globalIndex: Int, updatedEvent: BallEvent, inFirstInnings: Boolean = false) {
        if (inFirstInnings) {
            val current = _firstInningsEvents.value
            if (globalIndex < 0 || globalIndex >= current.size) return
            val updated = current.toMutableList().apply { set(globalIndex, updatedEvent) }
            _firstInningsEvents.value = updated
            rebuildFirstInningsSnapshot(updated)
            persistFirstInningsEvents()
        } else {
            val current = _events.value
            if (globalIndex < 0 || globalIndex >= current.size) return
            _events.value = current.toMutableList().apply { set(globalIndex, updatedEvent) }
            _state.value = reduce(_events.value)
                .copy(teamAName = currentTeamAName, teamBName = currentTeamBName)
            _fallOfWickets.value = computeFallOfWickets(_events.value)
            refreshMaidensFromEvents(_events.value)
            _consoleState.value = _consoleState.value.copy(
                currentInningsFallOfWickets = _fallOfWickets.value
            )
            refreshCurrentInningsOverSummaries()
            persistCurrentInningsEvents()
        }
    }
     /* (or the first-innings log when [inFirstInnings] is true).
     *
     * The innings aggregate [MatchState] is rebuilt by replaying the remaining events.
     * Per-player batting/bowling stats in [ScoringConsoleState] are **not** rebuilt
     * (the same simplification applied by [undo]).
     *
     * @param globalIndex   0-based position in the target event log.
     * @param inFirstInnings True when deleting from the archived first-innings log.
     */
    fun deleteBallEvent(globalIndex: Int, inFirstInnings: Boolean = false) {
        if (inFirstInnings) {
            val current = _firstInningsEvents.value
            if (globalIndex < 0 || globalIndex >= current.size) return
            val updated = current.filterIndexed { index, _ -> index != globalIndex }
            _firstInningsEvents.value = updated
            rebuildFirstInningsSnapshot(updated)
            persistFirstInningsEvents()
        } else {
            val current = _events.value
            if (globalIndex < 0 || globalIndex >= current.size) return
            _events.value = _events.value.filterIndexed { index, _ -> index != globalIndex }
            _state.value = reduce(_events.value)
                .copy(teamAName = currentTeamAName, teamBName = currentTeamBName)
            _fallOfWickets.value = computeFallOfWickets(_events.value)
            refreshMaidensFromEvents(_events.value)
            _consoleState.value = _consoleState.value.copy(
                currentInningsFallOfWickets = _fallOfWickets.value
            )
            refreshCurrentInningsOverSummaries()
            persistCurrentInningsEvents()
        }
    }
     /* replaying [firstEvents] through the reducer.
     *
     * Called after [replaceBallEvent] or [deleteBallEvent] modifies the first-innings log.
     * Only aggregate totals (runs, wickets, extras, overs, target) are updated; per-player
     * batting/bowling entries are left unchanged except for maiden counts, which are always
     * derived from the event log and therefore kept accurate.
     */
    private fun rebuildFirstInningsSnapshot(firstEvents: List<BallEvent>) {
        val firstState = reduce(firstEvents)
        val maidensMap = MaidenOverCalculator.compute(firstEvents)
        val updatedFirstBowling = _consoleState.value.firstInningsBowlingEntries.map { entry ->
            entry.copy(maidens = maidensMap[entry.player.id] ?: 0)
        }
        _consoleState.value = _consoleState.value.copy(
            firstInningsRuns     = firstState.runs,
            firstInningsWickets  = firstState.wickets,
            firstInningsExtras   = firstState.extras,
            firstInningsWides    = firstState.wides,
            firstInningsNoBalls  = firstState.noBalls,
            firstInningsByes     = firstState.byes,
            firstInningsLegByes  = firstState.legByes,
            firstInningsOvers    = firstState.overs,
            firstInningsBalls    = firstState.balls,
            target               = firstState.runs + 1,
            firstInningsBowlingEntries    = updatedFirstBowling,
            firstInningsFallOfWickets     = computeFallOfWickets(firstEvents),
            firstInningsOverSummaries     = OverSummaryCalculator.deriveOverSummaries(firstEvents)
        )
    }

    /**
     * Recompute maiden counts for the current innings from the event log and update the
     * bowling entries in [ScoringConsoleState].
     *
     * Maiden counts are derived entirely from [events] via [MaidenOverCalculator], so this
     * function can be called after any modification to the event log (undo, replace, delete)
     * and will always produce the correct result without needing a full console-state replay.
     */
    private fun refreshMaidensFromEvents(events: List<BallEvent>) {
        val maidensMap = MaidenOverCalculator.compute(events)
        val console = _consoleState.value
        val updatedBowling = console.allBowlingEntries.map { entry ->
            entry.copy(maidens = maidensMap[entry.player.id] ?: 0)
        }
        val updatedCurrentBowlerEntry = console.currentBowlerEntry?.let { cur ->
            updatedBowling.find { it.player.id == cur.player.id } ?: cur
        }
        _consoleState.value = console.copy(
            allBowlingEntries = updatedBowling,
            currentBowlerEntry = updatedCurrentBowlerEntry
        )
    }

    /**
     * Rebuilds all per-player batting/bowling statistics and batter-position state in
     * [_consoleState] by replaying [events] from scratch.
     *
     * This is the canonical post-undo refresh: rather than attempting to subtract values
     * manually, every stat is recomputed purely from the event log so the result is always
     * correct regardless of delivery type or order.
     *
     * The following fields are rebuilt:
     * - [ScoringConsoleState.allBattingEntries] — runs, balls, 4s, 6s, dismissal per batter
     * - [ScoringConsoleState.allBowlingEntries] — runs, overs, balls, wickets, maidens per bowler
     * - [ScoringConsoleState.striker] / [ScoringConsoleState.nonStriker] — current positions
     * - [ScoringConsoleState.currentBowler] — last bowler to bowl
     * - [ScoringConsoleState.strikerEntry] / [ScoringConsoleState.nonStrikerEntry] / [ScoringConsoleState.currentBowlerEntry]
     * - [ScoringConsoleState.currentPartnershipRuns] / [ScoringConsoleState.currentPartnershipBalls]
     * - [ScoringConsoleState.currentInningsFallOfWickets]
     * - [ScoringConsoleState.pendingAction] is cleared (undo removes the event that caused any pending action)
     *
     * Fields that belong to the innings lifecycle ([ScoringConsoleState.phase],
     * [ScoringConsoleState.inningsNumber], first-innings snapshot fields, target, etc.) are
     * preserved unchanged.
     */
    private fun rebuildConsoleFromEvents(events: List<BallEvent>) {
        val console = _consoleState.value
        if (console.phase == InningsPhase.SETUP || console.phase == InningsPhase.MATCH_COMPLETE) return

        if (events.isEmpty()) {
            val snapshot = inningsOpenersSnapshot[console.inningsNumber]
            if (snapshot != null) {
                // Setup was completed; restore the original opener assignments with zeroed stats.
                val strikerEntry = BattingEntry(player = snapshot.striker)
                val nonStrikerEntry = BattingEntry(player = snapshot.nonStriker)
                val bowlerEntry = BowlingEntry(player = snapshot.bowler)
                _consoleState.value = console.copy(
                    striker = snapshot.striker,
                    nonStriker = snapshot.nonStriker,
                    currentBowler = snapshot.bowler,
                    strikerEntry = strikerEntry,
                    nonStrikerEntry = nonStrikerEntry,
                    currentBowlerEntry = bowlerEntry,
                    allBattingEntries = listOf(strikerEntry, nonStrikerEntry),
                    allBowlingEntries = listOf(bowlerEntry),
                    pendingAction = null,
                    bowlerChangePending = false,
                    currentPartnershipRuns = 0,
                    currentPartnershipBalls = 0,
                    currentInningsFallOfWickets = emptyList()
                )
            } else {
                // No snapshot: fall back to existing reset logic (for old matches / app restarts).
                val resetBatting = console.allBattingEntries.map {
                    it.copy(runs = 0, balls = 0, fours = 0, sixes = 0, isOut = false, dismissal = null)
                }
                val resetBowling = console.allBowlingEntries.map {
                    it.copy(runs = 0, balls = 0, overs = 0, wickets = 0, maidens = 0)
                }
                _consoleState.value = console.copy(
                    allBattingEntries = resetBatting,
                    allBowlingEntries = resetBowling,
                    strikerEntry = resetBatting.find { it.player.id == console.striker?.id },
                    nonStrikerEntry = resetBatting.find { it.player.id == console.nonStriker?.id },
                    currentBowlerEntry = resetBowling.find { it.player.id == console.currentBowler?.id },
                    pendingAction = null,
                    bowlerChangePending = false,
                    currentPartnershipRuns = 0,
                    currentPartnershipBalls = 0,
                    currentInningsFallOfWickets = _fallOfWickets.value
                )
            }
            _currentPartnership.value = _currentPartnership.value?.copy(runs = 0, balls = 0)
            return
        }

        // ── Initialise per-player stat accumulators ─────────────────────────────────────────────
        // Use a LinkedHashMap to preserve the order in which players first appear in the log.
        val battingById = LinkedHashMap<String, BattingEntry>()
        val bowlingById = LinkedHashMap<String, BowlingEntry>()

        for (event in events) {
            event.striker?.let { if (it.id !in battingById) battingById[it.id] = BattingEntry(player = it) }
            event.nonStriker?.let { if (it.id !in battingById) battingById[it.id] = BattingEntry(player = it) }
            event.bowler?.let { if (it.id !in bowlingById) bowlingById[it.id] = BowlingEntry(player = it) }
        }

        // ── Replay events to accumulate stats ───────────────────────────────────────────────────
        for (event in events) {
            val striker = event.striker ?: continue
            val isWide   = event.extras.wides   > 0
            val isNoBall = event.extras.noBalls > 0
            val isBye    = event.extras.byes    > 0
            val isLegBye = event.extras.legByes > 0
            val strikerIsOut = event.wicket && event.dismissalDetail?.batter?.id == striker.id

            // Striker batting stats (mirrors updateConsoleAfterEvent logic)
            battingById[striker.id]?.let { entry ->
                battingById[striker.id] = when {
                    isWide -> entry
                    event.wicket && strikerIsOut ->
                        entry.copy(balls = entry.balls + 1, isOut = true, dismissal = event.dismissalDetail)
                    event.wicket ->
                        entry.copy(balls = entry.balls + 1)
                    isNoBall ->
                        entry.copy(
                            runs  = entry.runs  + event.runsOffBat,
                            fours = if (event.runsOffBat == 4) entry.fours + 1 else entry.fours,
                            sixes = if (event.runsOffBat == 6) entry.sixes + 1 else entry.sixes
                        )
                    isBye || isLegBye ->
                        entry.copy(balls = entry.balls + 1)
                    else ->
                        entry.copy(
                            runs  = entry.runs  + event.runsOffBat,
                            balls = entry.balls + 1,
                            fours = if (event.runsOffBat == 4) entry.fours + 1 else entry.fours,
                            sixes = if (event.runsOffBat == 6) entry.sixes + 1 else entry.sixes
                        )
                }
            }

            // Non-striker: only updated when dismissed (run out)
            if (event.wicket && !strikerIsOut) {
                event.nonStriker?.let { ns ->
                    battingById[ns.id]?.let { entry ->
                        battingById[ns.id] = entry.copy(isOut = true, dismissal = event.dismissalDetail)
                    }
                }
            }

            // Bowler stats (mirrors updateConsoleAfterEvent logic)
            event.bowler?.let { bowler ->
                bowlingById[bowler.id]?.let { entry ->
                    bowlingById[bowler.id] = when {
                        isWide ->
                            entry.copy(runs = entry.runs + event.extras.wides)
                        isNoBall ->
                            entry.copy(runs = entry.runs + event.extras.noBalls + event.runsOffBat)
                        event.wicket -> {
                            val (o, b) = incrementBall(entry.overs, entry.balls)
                            if (event.dismissalDetail?.bowlerCredited == true)
                                entry.copy(wickets = entry.wickets + 1, overs = o, balls = b)
                            else
                                entry.copy(overs = o, balls = b)
                        }
                        isBye || isLegBye -> {
                            val (o, b) = incrementBall(entry.overs, entry.balls)
                            entry.copy(overs = o, balls = b)
                        }
                        else -> {
                            val (o, b) = incrementBall(entry.overs, entry.balls)
                            entry.copy(runs = entry.runs + event.runsOffBat, overs = o, balls = b)
                        }
                    }
                }
            }
        }

        // Apply maiden counts (always derived from the full event log).
        val maidensMap = MaidenOverCalculator.compute(events)
        for (id in bowlingById.keys.toList()) {
            bowlingById[id] = bowlingById[id]!!.copy(maidens = maidensMap[id] ?: 0)
        }

        // ── Derive current player positions ─────────────────────────────────────────────────────
        val (currentStriker, currentNonStriker) = deriveCurrentBatters(events)
        val currentBowler = events.lastOrNull { it.bowler != null }?.bowler

        val allBatting = battingById.values.toList()
        val allBowling = bowlingById.values.toList()

        val strikerEntry = currentStriker?.let { s -> allBatting.find { it.player.id == s.id } }
        val nonStrikerEntry = currentNonStriker?.let { ns -> allBatting.find { it.player.id == ns.id } }
        val currentBowlerEntry = currentBowler?.let { b -> allBowling.find { it.player.id == b.id } }

        // ── Rebuild partnership counters ─────────────────────────────────────────────────────────
        // Partnership resets on each wicket; sum runs and balls from events since the last wicket.
        val lastWicketIdx = events.indexOfLast { it.wicket }
        val partnershipEvents = if (lastWicketIdx < 0) events else events.drop(lastWicketIdx + 1)
        val partnershipRuns  = partnershipEvents.sumOf { it.runsOffBat + it.extras.total }
        val partnershipBalls = partnershipEvents.count { it.countsAsBall }

        _consoleState.value = console.copy(
            striker              = currentStriker,
            nonStriker           = currentNonStriker,
            currentBowler        = currentBowler,
            strikerEntry         = strikerEntry,
            nonStrikerEntry      = nonStrikerEntry,
            currentBowlerEntry   = currentBowlerEntry,
            allBattingEntries    = allBatting,
            allBowlingEntries    = allBowling,
            pendingAction        = null,
            bowlerChangePending  = false,
            currentPartnershipRuns  = partnershipRuns,
            currentPartnershipBalls = partnershipBalls,
            currentInningsFallOfWickets = _fallOfWickets.value
        )

        // Keep the live partnership object in sync with the rebuilt counters.
        _currentPartnership.value = _currentPartnership.value?.copy(
            runs  = partnershipRuns,
            balls = partnershipBalls
        )
    }

    /**
     * Recompute over summaries for the current innings from the event log and update
     * [ScoringConsoleState.firstInningsOverSummaries] or
     * [ScoringConsoleState.secondInningsOverSummaries] depending on the active innings.
     *
     * Because over summaries are derived entirely from the event log they are always
     * correct after undo, replace, or delete without a full console-state replay.
     */
    private fun refreshCurrentInningsOverSummaries() {
        val summaries = OverSummaryCalculator.deriveOverSummaries(_events.value)
        _consoleState.value = if (_consoleState.value.inningsNumber == 1) {
            _consoleState.value.copy(firstInningsOverSummaries = summaries)
        } else {
            _consoleState.value.copy(secondInningsOverSummaries = summaries)
        }
    }

    fun resetMatch() {
        val matchId = _activeMatch.value?.localId
        Log.d("ResetFlow", "Reset requested for active match: ${_activeMatch.value?.displayTitle} (id=$matchId)")
        resumeJob?.cancel()
        _events.value = emptyList()
        _firstInningsEvents.value = emptyList()
        _state.value = MatchState()
        _consoleState.value = ScoringConsoleState()
        _fallOfWickets.value = emptyList()
        _activeMatch.value = null
        _currentPartnership.value = null
        _completedPartnerships.value = emptyList()
        _firstInningsCompletedPartnerships.value = emptyList()
        currentTeamAName = "Team A"
        currentTeamBName = "Team B"
        inningsOpenersSnapshot.clear()
        if (matchId != null) {
            viewModelScope.launch {
                MatchRepository.deleteAllBallEvents(matchId)
                Log.d("ResetFlow", "Ball events cleared from DB for match: $matchId")
            }
        }
        Log.d("ResetFlow", "In-memory scoring state cleared")
    }

    // ---------------------------------------------------------------------------
    // Player management
    // ---------------------------------------------------------------------------

    /** Called once when opening batters and the first bowler are confirmed. */
    fun setOpeners(striker: Player, nonStriker: Player, bowler: Player) {
        val match = _activeMatch.value
        if (match != null) {
            val inningsNum = _consoleState.value.inningsNumber
            val battingTeam = if (inningsNum == 1) match.battingFirst else match.bowlingFirst
            val bowlingTeam = if (inningsNum == 1) match.bowlingFirst else match.battingFirst
            listOf(striker to battingTeam, nonStriker to battingTeam, bowler to bowlingTeam)
                .forEach { (player, targetTeam) ->
                    if (!canAddPlayerToTeam(player, targetTeam, match)) {
                        Log.w("PlayerValidation", "Player ${player.name} blocked: already in opposing team")
                        _validationError.value = "This player is already part of the opposing team"
                        return
                    }
                }
        }
        val strikerEntry = BattingEntry(player = striker)
        val nonStrikerEntry = BattingEntry(player = nonStriker)
        val bowlerEntry = BowlingEntry(player = bowler)
        val inningsNumber = _consoleState.value.inningsNumber
        val phase = if (inningsNumber == 1) InningsPhase.FIRST_INNINGS
                    else InningsPhase.SECOND_INNINGS
        // Record the snapshot before updating console state so it is always available
        // to rebuildConsoleFromEvents if undo is pressed immediately after.
        inningsOpenersSnapshot[inningsNumber] =
            InningsOpenersSnapshot(striker = striker, nonStriker = nonStriker, bowler = bowler)
        _consoleState.value = _consoleState.value.copy(
            phase = phase,
            striker = striker,
            nonStriker = nonStriker,
            currentBowler = bowler,
            strikerEntry = strikerEntry,
            nonStrikerEntry = nonStrikerEntry,
            currentBowlerEntry = bowlerEntry,
            allBattingEntries = listOf(strikerEntry, nonStrikerEntry),
            allBowlingEntries = listOf(bowlerEntry),
            pendingAction = null,
            bowlerChangePending = false,
            currentPartnershipRuns = 0,
            currentPartnershipBalls = 0,
            inningsSetupCompleted = true
        )
        _currentPartnership.value = Partnership(
            strikerName = striker.name,
            nonStrikerName = nonStriker.name,
            runs = 0,
            balls = 0,
            startScore = _state.value.runs,
            endScore = 0,
            isCurrent = true
        )
        _completedPartnerships.value = emptyList()
    }

    /** Called after a wicket when the scorer picks the incoming batter. */
    fun selectNextBatter(player: Player) {
        Log.d("WicketFlow", "Next batter selected: ${player.name}")
        val match = _activeMatch.value
        if (match != null) {
            val console = _consoleState.value
            val battingTeam = if (console.inningsNumber == 1) match.battingFirst else match.bowlingFirst
            if (!canAddPlayerToTeam(player, battingTeam, match)) {
                Log.w("PlayerValidation", "Player ${player.name} blocked: already in opposing team")
                _validationError.value = "This player is already part of the opposing team"
                return
            }
        }
        val console = _consoleState.value
        val pendingSelect = console.pendingAction as? PendingAction.SelectNextBatter
        val replacingStriker = pendingSelect?.replacingStriker ?: true
        val newEntry = BattingEntry(player = player)
        val updatedAll = console.allBattingEntries + newEntry
        // If the over also ended when the wicket fell, chain into a bowler-change dialog.
        val nextPending = if (console.bowlerChangePending) {
            PendingAction.SelectBowler(availableBowlers())
        } else null
        _consoleState.value = console.copy(
            striker = if (replacingStriker) player else console.striker,
            nonStriker = if (replacingStriker) console.nonStriker else player,
            strikerEntry = if (replacingStriker) newEntry else console.strikerEntry,
            nonStrikerEntry = if (replacingStriker) console.nonStrikerEntry else newEntry,
            allBattingEntries = updatedAll,
            pendingAction = nextPending,
            bowlerChangePending = false,
            currentPartnershipRuns = 0,
            currentPartnershipBalls = 0
        )
        Log.d("WicketFlow", "pendingAction cleared after next batter selection (nextPending=${nextPending?.javaClass?.simpleName})")
        // Start a new partnership with the incoming batter and the remaining batter.
        val remainingName = if (replacingStriker) console.nonStriker?.name else console.striker?.name
        if (remainingName != null) {
            val newStriker = if (replacingStriker) player.name else remainingName
            val newNonStriker = if (replacingStriker) remainingName else player.name
            _currentPartnership.value = Partnership(
                strikerName = newStriker,
                nonStrikerName = newNonStriker,
                runs = 0,
                balls = 0,
                startScore = _state.value.runs,
                endScore = 0,
                isCurrent = true
            )
        }
    }

    /** Called at the end of each over when the scorer picks the new bowler. */
    fun changeBowler(player: Player) {
        val match = _activeMatch.value
        if (match != null) {
            val console = _consoleState.value
            val bowlingTeam = if (console.inningsNumber == 1) match.bowlingFirst else match.battingFirst
            if (!canAddPlayerToTeam(player, bowlingTeam, match)) {
                Log.w("PlayerValidation", "Player ${player.name} blocked: already in opposing team")
                _validationError.value = "This player is already part of the opposing team"
                return
            }
        }
        val console = _consoleState.value
        val existingEntry = console.allBowlingEntries.find { it.player.id == player.id }
        val entry = existingEntry ?: BowlingEntry(player = player)
        val updatedAll = if (existingEntry != null) console.allBowlingEntries
                         else console.allBowlingEntries + entry
        _consoleState.value = console.copy(
            currentBowler = player,
            currentBowlerEntry = entry,
            allBowlingEntries = updatedAll,
            pendingAction = null
        )
    }

    /**
     * Manually swaps the current striker and non-striker.
     *
     * This is a scorer-correction action only — it does not create a ball event and has no
     * effect on score totals, the event log, or any statistics.  It is a no-op when either
     * batter position is null.
     */
    fun swapStrike() {
        val console = _consoleState.value
        if (console.striker == null || console.nonStriker == null) return
        _consoleState.value = console.copy(
            striker = console.nonStriker,
            nonStriker = console.striker,
            strikerEntry = console.nonStrikerEntry,
            nonStrikerEntry = console.strikerEntry
        )
    }

    // ---------------------------------------------------------------------------
    // Player management — add during active match
    // ---------------------------------------------------------------------------

    /**
     * Add a [player] to a team in the active match after the match has already started.
     *
     * @param player The new player to add.
     * @param addToBattingTeam If true, adds to the currently batting team; otherwise to the bowling team.
     *
     * Both [_activeMatch] (the in-memory ViewModel state) and [MatchRepository] are updated so
     * that the live scoring session and the match list remain consistent.
     */
    fun addPlayerToTeam(player: Player, addToBattingTeam: Boolean) {
        val match = _activeMatch.value ?: return
        val console = _consoleState.value
        val battingTeam = if (console.inningsNumber == 1) match.battingFirst else match.bowlingFirst
        val bowlingTeam = if (console.inningsNumber == 1) match.bowlingFirst else match.battingFirst
        val targetTeam = if (addToBattingTeam) battingTeam else bowlingTeam

        // Defensive re-check to guard against race conditions.
        if (!canAddPlayerToTeam(player, targetTeam, match)) {
            Log.w("PlayerValidation", "Player ${player.name} blocked: already in opposing team")
            _validationError.value = "This player is already part of the opposing team"
            return
        }

        val updatedTeam = targetTeam.copy(players = targetTeam.players + player)
        val updatedMatch = match.updateTeamRef(targetTeam, updatedTeam)
        _activeMatch.value = updatedMatch
        MatchRepository.updateMatch(updatedMatch)
        Log.d("PlayerValidation", "Player ${player.name} added to ${targetTeam.name}")
    }

    // ---------------------------------------------------------------------------
    // Innings management
    // ---------------------------------------------------------------------------

    /**
     * End the first innings manually.
     * Saves the first-innings totals and scorecard snapshot, then enters the
     * [InningsPhase.INNINGS_BREAK] state so the UI can display the target
     * before the scorer explicitly starts the second innings.
     *
     * Also persists the match status as [MatchStatus.INNINGS_BREAK] so that
     * [initFromMatch] can correctly restore the innings-break UI after an app restart.
     */
    fun endFirstInnings() {
        val state = _state.value
        val console = _consoleState.value
        // Snapshot the first-innings event log so it can be displayed in the ball timeline
        // even after the second innings event log replaces _events.
        _firstInningsEvents.value = _events.value
        _consoleState.value = console.copy(
            phase = InningsPhase.INNINGS_BREAK,
            firstInningsRuns = state.runs,
            firstInningsWickets = state.wickets,
            firstInningsExtras = state.extras,
            firstInningsWides = state.wides,
            firstInningsNoBalls = state.noBalls,
            firstInningsByes = state.byes,
            firstInningsLegByes = state.legByes,
            firstInningsOvers = state.overs,
            firstInningsBalls = state.balls,
            firstInningsBattingEntries = console.allBattingEntries,
            firstInningsBowlingEntries = console.allBowlingEntries,
            firstInningsFallOfWickets = console.currentInningsFallOfWickets,
            firstInningsOverSummaries = OverSummaryCalculator.deriveOverSummaries(_events.value),
            currentInningsFallOfWickets = emptyList(),
            target = state.runs + 1
        )
        // Snapshot completed partnerships; also finalize any ongoing partnership.
        val ongoing = _currentPartnership.value?.copy(endScore = state.runs, isCurrent = false)
        _firstInningsCompletedPartnerships.value =
            _completedPartnerships.value + listOfNotNull(ongoing)
        _currentPartnership.value = null
        _completedPartnerships.value = emptyList()
        // Persist match status so innings-break state survives app restart.
        _activeMatch.value?.let { match ->
            val updated = match.copy(status = MatchStatus.INNINGS_BREAK)
            _activeMatch.value = updated
            MatchRepository.updateMatch(updated)
        }
    }

    /**
     * Start the second innings after the innings break.
     * Swaps batting/bowling sides, resets the event log, and transitions to
     * [InningsPhase.SETUP] (or [InningsPhase.SECOND_INNINGS] if no players are set).
     */
    fun startSecondInnings() {
        val match = _activeMatch.value ?: return
        val console = _consoleState.value

        currentTeamAName = match.bowlingFirst.name
        currentTeamBName = match.battingFirst.name
        _events.value = emptyList()
        _fallOfWickets.value = emptyList()
        _state.value = MatchState(
            teamAName = currentTeamAName,
            teamBName = currentTeamBName
        )

        val hasPlayers = match.bowlingFirst.players.isNotEmpty() &&
                match.battingFirst.players.isNotEmpty()
        _consoleState.value = ScoringConsoleState(
            inningsNumber = 2,
            phase = if (hasPlayers) InningsPhase.SETUP else InningsPhase.SECOND_INNINGS,
            battingTeamName = match.bowlingFirst.name,
            bowlingTeamName = match.battingFirst.name,
            firstInningsRuns = console.firstInningsRuns,
            firstInningsWickets = console.firstInningsWickets,
            firstInningsExtras = console.firstInningsExtras,
            firstInningsWides = console.firstInningsWides,
            firstInningsNoBalls = console.firstInningsNoBalls,
            firstInningsByes = console.firstInningsByes,
            firstInningsLegByes = console.firstInningsLegByes,
            firstInningsOvers = console.firstInningsOvers,
            firstInningsBalls = console.firstInningsBalls,
            firstInningsBattingEntries = console.firstInningsBattingEntries,
            firstInningsBowlingEntries = console.firstInningsBowlingEntries,
            firstInningsFallOfWickets = console.firstInningsFallOfWickets,
            firstInningsOverSummaries = console.firstInningsOverSummaries,
            currentInningsFallOfWickets = emptyList(),
            target = console.target
        )
        // Restore match status to IN_PROGRESS for the second innings.
        val updated = match.copy(status = MatchStatus.IN_PROGRESS)
        _activeMatch.value = updated
        MatchRepository.updateMatch(updated)
    }

    /**
     * Mark the match as complete.
     *
     * Also persists the match status as [MatchStatus.COMPLETED] so that the match list
     * correctly reflects the outcome after an app restart.
     */
    fun endMatch() {
        _consoleState.value = _consoleState.value.copy(
            phase = InningsPhase.MATCH_COMPLETE,
            pendingAction = null
        )
        _activeMatch.value?.let { match ->
            val updated = match.copy(status = MatchStatus.COMPLETED)
            _activeMatch.value = updated
            MatchRepository.updateMatch(updated)
        }
    }

    /**
     * Called when the scorer selects "No more players / All out" in the next-batter dialog.
     *
     * Clears the pending batter selection and ends the current innings immediately:
     * - First innings → moves to [InningsPhase.INNINGS_BREAK] (preserves total, sets target).
     * - Second innings → marks match as [InningsPhase.MATCH_COMPLETE].
     */
    fun endInningsAsAllOut() {
        Log.d("WicketFlow", "All out selected by scorer")
        val console = _consoleState.value
        // Clear pending action first so scoring controls are unblocked for the transition.
        _consoleState.value = console.copy(pendingAction = null, bowlerChangePending = false)
        if (console.inningsNumber == 1) {
            Log.d("WicketFlow", "Innings ended due to all out — moving to innings break")
            endFirstInnings()
        } else {
            Log.d("WicketFlow", "Innings ended due to all out — match completed")
            endMatch()
        }
    }

    // ---------------------------------------------------------------------------
    // Initialisation
    // ---------------------------------------------------------------------------

    /**
     * Initialise (or re-initialise) the scoring session from a [Match] entity.
     * Clears the current event log and seeds [MatchState] with the team names
     * derived from the match's batting/bowling order.
     *
     * After clearing state, launches a coroutine to load any persisted [BallEvent]s for this
     * match and replays them through the reducer, so an in-progress match is fully restored
     * after an app restart without requiring manual re-entry of events.
     */
    fun initFromMatch(match: Match) {
        _activeMatch.value = match
        currentTeamAName = match.battingFirst.name
        currentTeamBName = match.bowlingFirst.name
        _events.value = emptyList()
        _firstInningsEvents.value = emptyList()
        _fallOfWickets.value = emptyList()
        _currentPartnership.value = null
        _completedPartnerships.value = emptyList()
        _firstInningsCompletedPartnerships.value = emptyList()
        _state.value = MatchState(
            teamAName = currentTeamAName,
            teamBName = currentTeamBName
        )
        // Start with FIRST_INNINGS phase so that the setup dialog is driven by
        // needsInningsSetup (missing players) rather than by phase alone.
        // resumePersistedState will correct the phase to INNINGS_BREAK / SECOND_INNINGS /
        // MATCH_COMPLETE if the persisted match status requires it.
        _consoleState.value = ScoringConsoleState(
            inningsNumber = 1,
            phase = InningsPhase.FIRST_INNINGS,
            battingTeamName = match.battingFirst.name,
            bowlingTeamName = match.bowlingFirst.name
        )
        Log.d("ResumeFlow", "initFromMatch: ${match.displayTitle}, status=${match.status}")

        // Load persisted events and rebuild state so the match can be resumed after restart.
        // Cancel any in-flight resumption from a previous initFromMatch call.
        resumeJob?.cancel()
        _isResuming.value = true
        resumeJob = viewModelScope.launch {
            try {
                Log.d("ResumeFlow", "Resuming match ${match.localId} (${match.displayTitle})")
                // Fetch remote events first so the most up-to-date log is available, including
                // when the match is opened on a new device.  Local Room DB is updated in place;
                // if the remote fetch fails, the existing local data is used as fallback.
                MatchRepository.syncMatchEvents(match.localId)
                // Check whether the match has started before loading all events so the
                // innings-setup decision uses a single source of truth (hasMatchStarted).
                val hasMatchStarted = MatchRepository.hasMatchStarted(match.localId)
                Log.d("ResumeFlow", "hasMatchStarted=$hasMatchStarted for match ${match.localId}")
                if (!hasMatchStarted) {
                    Log.d("ResumeFlow", "No persisted events — fresh match, awaiting setup")
                    // Fix existing data: a match with no events should always be NOT_STARTED.
                    val currentMatch = _activeMatch.value
                    if (currentMatch != null &&
                        currentMatch.status != MatchStatus.NOT_STARTED &&
                        currentMatch.status != MatchStatus.COMPLETED
                    ) {
                        Log.d("ResumeFlow", "Correcting match ${currentMatch.localId} status from ${currentMatch.status} → NOT_STARTED (no events)")
                        val fixed = currentMatch.copy(status = MatchStatus.NOT_STARTED)
                        _activeMatch.value = fixed
                        MatchRepository.updateMatch(fixed)
                    }
                    return@launch
                }
                val (firstEvents, secondEvents) = MatchRepository.loadAllBallEvents(match.localId)
                Log.d("ResumeFlow", "Rebuilding match state from ${firstEvents.size + secondEvents.size} events")
                // Fix existing data: a match with events should not be NOT_STARTED.
                val currentMatch = _activeMatch.value
                if (currentMatch != null && currentMatch.status == MatchStatus.NOT_STARTED) {
                    Log.d("ResumeFlow", "Correcting match ${currentMatch.localId} status from NOT_STARTED → IN_PROGRESS (events exist)")
                    val fixed = currentMatch.copy(status = MatchStatus.IN_PROGRESS)
                    _activeMatch.value = fixed
                    MatchRepository.updateMatch(fixed)
                    resumePersistedState(fixed, firstEvents, secondEvents)
                } else {
                    resumePersistedState(match, firstEvents, secondEvents)
                }
            } finally {
                _isResuming.value = false
            }
        }
    }

    /**
     * Rebuild scoring state from persisted [BallEvent] lists after an app restart.
     *
     * Replays events through [reduce] to produce the correct aggregate [MatchState], restores
     * the fall-of-wickets list, and sets [ScoringConsoleState] to the correct [InningsPhase]
     * so that the UI reflects the saved match state rather than forcing a fresh openers
     * selection every time.
     *
     * The current bowler is reconstructed from the last event that carries a bowler reference.
     * Striker and non-striker are reconstructed from the last stamped event by applying the same
     * rotation and wicket-replacement logic used during live scoring.  Events recorded before
     * batter-stamping was introduced (null striker) fall back gracefully: [ScoringScreen] detects
     * the missing players via [needsInningsSetup] and shows the setup dialog.
     *
     * Four resume scenarios are handled:
     * 1. **Match completed** — match.status is [MatchStatus.COMPLETED]; phase restored to
     *    [InningsPhase.MATCH_COMPLETE] so no setup dialog is shown.
     * 2. **Second innings in progress** — both innings have events; phase restored to
     *    [InningsPhase.SECOND_INNINGS].
     * 3. **Innings break** — only first-innings events, match status is [MatchStatus.INNINGS_BREAK].
     * 4. **First innings in progress** — only first-innings events, match still [MatchStatus.IN_PROGRESS];
     *    phase restored to [InningsPhase.FIRST_INNINGS].
     */
    private fun resumePersistedState(
        match: Match,
        firstEvents: List<BallEvent>,
        secondEvents: List<BallEvent>
    ) {
        Log.d("ResumeFlow", "resumePersistedState: status=${match.status}, " +
                "firstEvents=${firstEvents.size}, secondEvents=${secondEvents.size}")
        when {
            // ── 1. Match already completed ────────────────────────────────────────────
            match.status == MatchStatus.COMPLETED -> {
                if (secondEvents.isNotEmpty()) {
                    val firstState = reduce(firstEvents)
                    _firstInningsEvents.value = firstEvents
                    currentTeamAName = match.bowlingFirst.name
                    currentTeamBName = match.battingFirst.name
                    _events.value = secondEvents
                    _state.value = reduce(secondEvents)
                        .copy(teamAName = currentTeamAName, teamBName = currentTeamBName)
                    _fallOfWickets.value = computeFallOfWickets(secondEvents)
                    _consoleState.value = ScoringConsoleState(
                        inningsNumber = 2,
                        phase = InningsPhase.MATCH_COMPLETE,
                        battingTeamName = match.bowlingFirst.name,
                        bowlingTeamName = match.battingFirst.name,
                        firstInningsRuns = firstState.runs,
                        firstInningsWickets = firstState.wickets,
                        firstInningsExtras = firstState.extras,
                        firstInningsWides = firstState.wides,
                        firstInningsNoBalls = firstState.noBalls,
                        firstInningsByes = firstState.byes,
                        firstInningsLegByes = firstState.legByes,
                        firstInningsOvers = firstState.overs,
                        firstInningsBalls = firstState.balls,
                        target = firstState.runs + 1,
                        firstInningsFallOfWickets = computeFallOfWickets(firstEvents),
                        firstInningsOverSummaries = OverSummaryCalculator.deriveOverSummaries(firstEvents),
                        secondInningsOverSummaries = OverSummaryCalculator.deriveOverSummaries(secondEvents),
                        currentInningsFallOfWickets = _fallOfWickets.value
                    )
                } else if (firstEvents.isNotEmpty()) {
                    _events.value = firstEvents
                    _state.value = reduce(firstEvents)
                        .copy(teamAName = currentTeamAName, teamBName = currentTeamBName)
                    _fallOfWickets.value = computeFallOfWickets(firstEvents)
                    _consoleState.value = ScoringConsoleState(
                        inningsNumber = 1,
                        phase = InningsPhase.MATCH_COMPLETE,
                        battingTeamName = match.battingFirst.name,
                        bowlingTeamName = match.bowlingFirst.name,
                        firstInningsOverSummaries = OverSummaryCalculator.deriveOverSummaries(firstEvents),
                        currentInningsFallOfWickets = _fallOfWickets.value
                    )
                }
                Log.d("ResumeFlow", "Restored: match complete")
            }

            // ── 2. Second innings in progress ─────────────────────────────────────────
            secondEvents.isNotEmpty() -> {
                val firstState = reduce(firstEvents)
                _firstInningsEvents.value = firstEvents
                // In the second innings the batting team is the one that bowled first.
                currentTeamAName = match.bowlingFirst.name
                currentTeamBName = match.battingFirst.name
                _events.value = secondEvents
                _state.value = reduce(secondEvents)
                    .copy(teamAName = currentTeamAName, teamBName = currentTeamBName)
                _fallOfWickets.value = computeFallOfWickets(secondEvents)
                // Reconstruct the current bowler from the last stamped ball event.
                val currentBowler = secondEvents.lastOrNull { it.bowler != null }?.bowler
                // Reconstruct striker and non-striker by replaying the last delivery's
                // rotation/wicket logic on the most recent stamped batter positions.
                val (striker, nonStriker) = deriveCurrentBatters(secondEvents)
                val strikerEntry = striker?.let { BattingEntry(player = it) }
                val nonStrikerEntry = nonStriker?.let { BattingEntry(player = it) }
                val bowlerEntry = currentBowler?.let { BowlingEntry(player = it) }
                // Mark setup as completed when players can be derived from the event log so
                // that ScoringScreen does not show the innings-setup popup on resume.
                // When striker is null, the events pre-date batter-stamping; setup was never
                // recorded and the popup is legitimately needed to gather player information.
                val setupCompleted = striker != null
                _consoleState.value = ScoringConsoleState(
                    inningsNumber = 2,
                    phase = InningsPhase.SECOND_INNINGS,
                    battingTeamName = match.bowlingFirst.name,
                    bowlingTeamName = match.battingFirst.name,
                    firstInningsRuns = firstState.runs,
                    firstInningsWickets = firstState.wickets,
                    firstInningsExtras = firstState.extras,
                    firstInningsWides = firstState.wides,
                    firstInningsNoBalls = firstState.noBalls,
                    firstInningsByes = firstState.byes,
                    firstInningsLegByes = firstState.legByes,
                    firstInningsOvers = firstState.overs,
                    firstInningsBalls = firstState.balls,
                    target = firstState.runs + 1,
                    striker = striker,
                    nonStriker = nonStriker,
                    currentBowler = currentBowler,
                    strikerEntry = strikerEntry,
                    nonStrikerEntry = nonStrikerEntry,
                    currentBowlerEntry = bowlerEntry,
                    allBattingEntries = listOfNotNull(strikerEntry, nonStrikerEntry),
                    allBowlingEntries = listOfNotNull(bowlerEntry),
                    firstInningsFallOfWickets = computeFallOfWickets(firstEvents),
                    firstInningsOverSummaries = OverSummaryCalculator.deriveOverSummaries(firstEvents),
                    secondInningsOverSummaries = OverSummaryCalculator.deriveOverSummaries(secondEvents),
                    currentInningsFallOfWickets = _fallOfWickets.value,
                    inningsSetupCompleted = setupCompleted
                )
                Log.d("ResumeFlow", "Restored: 2nd innings in progress " +
                        "(${secondEvents.size} balls), bowler=${currentBowler?.name}, " +
                        "striker=${striker?.name}, nonStriker=${nonStriker?.name}, " +
                        "setupCompleted=$setupCompleted")
            }

            // ── 3. Innings break ──────────────────────────────────────────────────────
            firstEvents.isNotEmpty() && match.status == MatchStatus.INNINGS_BREAK -> {
                val firstState = reduce(firstEvents)
                _firstInningsEvents.value = firstEvents
                _consoleState.value = ScoringConsoleState(
                    inningsNumber = 1,
                    phase = InningsPhase.INNINGS_BREAK,
                    battingTeamName = match.battingFirst.name,
                    bowlingTeamName = match.bowlingFirst.name,
                    firstInningsRuns = firstState.runs,
                    firstInningsWickets = firstState.wickets,
                    firstInningsExtras = firstState.extras,
                    firstInningsWides = firstState.wides,
                    firstInningsNoBalls = firstState.noBalls,
                    firstInningsByes = firstState.byes,
                    firstInningsLegByes = firstState.legByes,
                    firstInningsOvers = firstState.overs,
                    firstInningsBalls = firstState.balls,
                    target = firstState.runs + 1,
                    firstInningsFallOfWickets = computeFallOfWickets(firstEvents),
                    firstInningsOverSummaries = OverSummaryCalculator.deriveOverSummaries(firstEvents)
                )
                Log.d("ResumeFlow", "Restored: innings break, " +
                        "1st inn total=${firstState.runs}/${firstState.wickets}")
            }

            // ── 4. First innings in progress ──────────────────────────────────────────
            firstEvents.isNotEmpty() -> {
                _events.value = firstEvents
                _state.value = reduce(firstEvents)
                    .copy(teamAName = currentTeamAName, teamBName = currentTeamBName)
                _fallOfWickets.value = computeFallOfWickets(firstEvents)
                // Reconstruct the current bowler from the last stamped ball event.
                val currentBowler = firstEvents.lastOrNull { it.bowler != null }?.bowler
                // Reconstruct striker and non-striker by replaying the last delivery's
                // rotation/wicket logic on the most recent stamped batter positions.
                val (striker, nonStriker) = deriveCurrentBatters(firstEvents)
                val strikerEntry = striker?.let { BattingEntry(player = it) }
                val nonStrikerEntry = nonStriker?.let { BattingEntry(player = it) }
                val bowlerEntry = currentBowler?.let { BowlingEntry(player = it) }
                // Mark setup as completed when players can be derived from the event log so
                // that ScoringScreen does not show the innings-setup popup on resume.
                // When striker is null (events pre-date batter-stamping) setup was never
                // recorded and the popup is legitimately needed.
                val setupCompleted = striker != null
                _consoleState.value = ScoringConsoleState(
                    inningsNumber = 1,
                    phase = InningsPhase.FIRST_INNINGS,
                    battingTeamName = match.battingFirst.name,
                    bowlingTeamName = match.bowlingFirst.name,
                    striker = striker,
                    nonStriker = nonStriker,
                    currentBowler = currentBowler,
                    strikerEntry = strikerEntry,
                    nonStrikerEntry = nonStrikerEntry,
                    currentBowlerEntry = bowlerEntry,
                    allBattingEntries = listOfNotNull(strikerEntry, nonStrikerEntry),
                    allBowlingEntries = listOfNotNull(bowlerEntry),
                    firstInningsOverSummaries = OverSummaryCalculator.deriveOverSummaries(firstEvents),
                    currentInningsFallOfWickets = _fallOfWickets.value,
                    inningsSetupCompleted = setupCompleted
                )
                Log.d("ResumeFlow", "Restored: 1st innings in progress " +
                        "(${firstEvents.size} balls), bowler=${currentBowler?.name}, " +
                        "striker=${striker?.name}, nonStriker=${nonStriker?.name}, " +
                        "setupCompleted=$setupCompleted")
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    /**
     * Derives the current striker and non-striker from a sequence of persisted [BallEvent]s
     * after an app restart.
     *
     * Each event carries the pre-delivery batter positions in its [BallEvent.striker] and
     * [BallEvent.nonStriker] fields (stamped by [addBallEvent]).  This function locates the
     * last event that has a non-null striker stamp, then applies the same rotation and
     * wicket-replacement logic used by [updateConsoleAfterEvent] to compute the post-delivery
     * batting positions.
     *
     * Returns `Pair(null, null)` for events recorded before batter-stamping was introduced,
     * allowing [ScoringScreen] to fall back to the innings-setup dialog for those matches.
     */
    private fun deriveCurrentBatters(events: List<BallEvent>): Pair<Player?, Player?> {
        val lastIdx = events.indexOfLast { it.striker != null }
        if (lastIdx < 0) return Pair(null, null)

        val event = events[lastIdx]
        val preStriker = event.striker
        val preNonStriker = event.nonStriker

        val wicketFell = event.wicket
        val strikerIsOut = wicketFell &&
                event.dismissalDetail?.batter?.name == preStriker?.name

        // Determine whether this delivery ended the current over.  Compare MatchState ball
        // counts before and after this specific delivery to detect the over boundary.
        val stateAfter = reduce(events.take(lastIdx + 1))
        val stateBefore = if (lastIdx > 0) reduce(events.take(lastIdx)) else MatchState()
        val overEnded = event.countsAsBall &&
                stateAfter.balls == 0 &&
                stateAfter.overs > stateBefore.overs

        // Odd-runs rotation: wides and no-balls do not rotate strike.
        val isWide = event.extras.wides > 0
        val isNoBall = event.extras.noBalls > 0
        val oddRuns = !isWide && !isNoBall &&
                (event.runsOffBat + event.extras.byes + event.extras.legByes) % 2 == 1

        // Mirror the three-step rotation logic from updateConsoleAfterEvent.

        // Step 1: run-crossing rotation
        val (crossedStriker, crossedNonStriker) = if (oddRuns) {
            Pair(preNonStriker, preStriker)
        } else {
            Pair(preStriker, preNonStriker)
        }

        // Step 2: over-end rotation
        val (finalStriker, finalNonStriker) = if (overEnded) {
            Pair(crossedNonStriker, crossedStriker)
        } else {
            Pair(crossedStriker, crossedNonStriker)
        }

        // Step 3: wicket — null out the dismissed batter's final position
        val dismissedId: String? = when {
            !wicketFell -> null
            strikerIsOut -> preStriker?.id
            else -> preNonStriker?.id
        }
        return when {
            dismissedId == null -> Pair(finalStriker, finalNonStriker)
            finalStriker?.id == dismissedId -> Pair(null, finalNonStriker)
            finalNonStriker?.id == dismissedId -> Pair(finalStriker, null)
            else -> Pair(finalStriker, finalNonStriker)
        }
    }

    /**
     * Returns batting-team players that are eligible to be the next incoming batter.
     *
     * A player is excluded when they are:
     * - the current striker
     * - the current non-striker
     * - already dismissed (has a batting entry with [BattingEntry.isOut] == true)
     *
     * The list is derived entirely from the team roster stored in [_activeMatch], so it
     * correctly reflects players added mid-match via [addPlayerToTeam].
     */
    fun eligibleNextBatters(): List<Player> {
        val match = _activeMatch.value ?: return emptyList()
        val console = _consoleState.value
        val battingTeam = if (console.inningsNumber == 1) match.battingFirst else match.bowlingFirst
        val dismissedIds = console.allBattingEntries
            .filter { it.isOut }.map { it.player.id }.toSet()
        val currentIds = setOfNotNull(console.striker?.id, console.nonStriker?.id)
        return getEligiblePlayersForTeam(battingTeam.id, match)
            .filter { it.id !in dismissedIds && it.id !in currentIds }
    }

    private fun availableBowlers(): List<Player> {
        val match = _activeMatch.value ?: return emptyList()
        val console = _consoleState.value
        val bowlingTeam = if (console.inningsNumber == 1) match.bowlingFirst else match.battingFirst
        val lastBowlerId = console.currentBowler?.id
        // The same bowler cannot bowl consecutive overs
        return getEligiblePlayersForTeam(bowlingTeam.id, match)
            .filter { it.id != lastBowlerId }
    }

    /**
     * Fire-and-forget persistence of the current-innings event log.
     *
     * Saves [_events] for the innings indicated by [ScoringConsoleState.inningsNumber].
     * Called after every mutation that changes the active-innings event log
     * (add, undo, replace, delete).
     *
     * A [persistMutex] ensures successive saves are serialised so rapid mutations
     * (e.g. undo spam) cannot produce an out-of-order write.
     */
    private fun persistCurrentInningsEvents() {
        val match = _activeMatch.value ?: return
        val inningsNumber = _consoleState.value.inningsNumber
        val events = _events.value
        viewModelScope.launch {
            persistMutex.withLock {
                MatchRepository.saveBallEvents(match.localId, inningsNumber, events)
            }
        }
    }

    /**
     * Fire-and-forget persistence of the first-innings event log.
     *
     * Saves [_firstInningsEvents] as inningsNumber 1.
     * Called after a [replaceBallEvent] or [deleteBallEvent] that modifies the
     * archived first-innings log.
     *
     * A [persistMutex] ensures successive saves are serialised.
     */
    private fun persistFirstInningsEvents() {
        val match = _activeMatch.value ?: return
        val events = _firstInningsEvents.value
        viewModelScope.launch {
            persistMutex.withLock {
                MatchRepository.saveBallEvents(match.localId, 1, events)
            }
        }
    }

    private fun incrementBall(overs: Int, balls: Int): Pair<Int, Int> =
        if (balls + 1 >= 6) Pair(overs + 1, 0) else Pair(overs, balls + 1)

    /**
     * Computes the fall-of-wickets list from a sequence of [BallEvent]s.
     *
     * Scans through [events] in order, maintaining running totals for runs, overs, and balls.
     * Each time a wicket is encountered a [FallOfWicket] entry is appended with the score and
     * over at which the wicket fell.
     */
    private fun computeFallOfWickets(events: List<BallEvent>): List<FallOfWicket> {
        val result = mutableListOf<FallOfWicket>()
        var wicketCount = 0
        var runs = 0
        var overs = 0
        var balls = 0
        for (event in events) {
            runs += event.runsOffBat + event.extras.total
            if (event.countsAsBall) {
                balls++
                if (balls >= 6) {
                    overs++
                    balls = 0
                }
            }
            if (event.wicket && event.dismissalDetail != null) {
                wicketCount++
                result.add(
                    FallOfWicket(
                        wicketNumber = wicketCount,
                        batterName = event.dismissalDetail.batter.name,
                        teamScore = runs,
                        overs = "$overs.$balls",
                        dismissal = event.dismissalDetail.toScorecardString()
                    )
                )
            }
        }
        return result
    }

    /** Replace all references to [old] team with [updated] inside the match. */
    private fun Match.updateTeamRef(old: Team, updated: Team): Match = copy(
        teamA = if (teamA.id == old.id) updated else teamA,
        teamB = if (teamB.id == old.id) updated else teamB,
        battingFirst = if (battingFirst.id == old.id) updated else battingFirst,
        bowlingFirst = if (bowlingFirst.id == old.id) updated else bowlingFirst,
        tossWinner = if (tossWinner.id == old.id) updated else tossWinner
    )
}
