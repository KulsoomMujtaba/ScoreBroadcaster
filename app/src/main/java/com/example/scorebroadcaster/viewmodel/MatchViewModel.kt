package com.example.scorebroadcaster.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.scorebroadcaster.data.InningsPhase
import com.example.scorebroadcaster.data.MatchState
import com.example.scorebroadcaster.data.PendingAction
import com.example.scorebroadcaster.data.ScoreEvent
import com.example.scorebroadcaster.data.ScoringConsoleState
import com.example.scorebroadcaster.data.entity.BattingEntry
import com.example.scorebroadcaster.data.entity.BowlingEntry
import com.example.scorebroadcaster.data.entity.FallOfWicket
import com.example.scorebroadcaster.data.entity.Match
import com.example.scorebroadcaster.data.entity.Partnership
import com.example.scorebroadcaster.data.entity.Player
import com.example.scorebroadcaster.data.entity.Team
import com.example.scorebroadcaster.data.toBallEvent
import com.example.scorebroadcaster.domain.BallEvent
import com.example.scorebroadcaster.domain.MaidenOverCalculator
import com.example.scorebroadcaster.domain.reduce
import com.example.scorebroadcaster.repository.MatchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
     * Completed partnerships from the first innings.
     * Populated when the first innings ends; stays empty until then.
     */
    private val _firstInningsCompletedPartnerships = MutableStateFlow<List<Partnership>>(emptyList())
    val firstInningsCompletedPartnerships: StateFlow<List<Partnership>> = _firstInningsCompletedPartnerships.asStateFlow()

    // Preserved team names so they survive repeated reduce() calls
    private var currentTeamAName = "Team A"
    private var currentTeamBName = "Team B"

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
        // Stamp the current bowler onto the event so MaidenOverCalculator can derive maiden
        // counts from the event log without needing any external player-tracking state.
        val stamped = if (ballEvent.bowler == null) {
            ballEvent.copy(bowler = _consoleState.value.currentBowler)
        } else {
            ballEvent
        }
        val prevState = _state.value
        _events.value = _events.value + stamped
        val newState = reduce(_events.value)
            .copy(teamAName = currentTeamAName, teamBName = currentTeamBName)
        _state.value = newState
        if (stamped.wicket) {
            _fallOfWickets.value = computeFallOfWickets(_events.value)
        }
        updateConsoleAfterEvent(stamped, prevState, newState)
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
        // Strike rotation rules:
        //  - Striker wicket: new batter comes in at striker's end (striker = null).
        //  - Non-striker wicket (e.g. run out): new batter comes in at non-striker's end.
        //  - Over end + odd runs: rotations cancel each other out (no net change).
        //  - Over end OR odd runs (not both): rotate striker and non-striker.
        val (rotatedStriker, rotatedNonStriker) = when {
            wicketFell && strikerIsOut -> Pair(null, console.nonStriker)
            wicketFell && !strikerIsOut -> Pair(console.striker, null)
            overEnded && oddRuns -> Pair(console.striker, console.nonStriker)
            overEnded || oddRuns -> Pair(console.nonStriker, console.striker)
            else -> Pair(console.striker, console.nonStriker)
        }

        // --- Determine pending action ---
        val (pendingAction, bowlerChangePending) = when {
            wicketFell -> {
                // All out when 10 wickets have fallen (only 1 batter left – can't form a partnership).
                val allOut = newState.wickets >= 10
                if (!allOut) {
                    val remaining = eligibleNextBatters()
                    Log.d("WicketFlow", "pendingAction set to SelectNextBatter (${remaining.size} eligible team players, replacingStriker=$strikerIsOut)")
                    Pair(PendingAction.SelectNextBatter(remaining, replacingStriker = strikerIsOut), overEnded)
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

        _consoleState.value = console.copy(
            striker = rotatedStriker,
            nonStriker = rotatedNonStriker,
            strikerEntry = updatedAllBatting.find { it.player.id == rotatedStriker?.id },
            nonStrikerEntry = updatedAllBatting.find { it.player.id == rotatedNonStriker?.id },
            currentBowlerEntry = bowlerEntryWithMaidens,
            allBattingEntries = updatedAllBatting,
            allBowlingEntries = bowlingWithMaidens,
            pendingAction = pendingAction,
            bowlerChangePending = bowlerChangePending
        )

        // --- Update current partnership ---
        val partnershipRuns = event.runsOffBat + event.extras.total
        val partnershipBalls = if (event.countsAsBall) 1 else 0
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
    }

    fun undo() {
        if (_events.value.isNotEmpty()) {
            _events.value = _events.value.dropLast(1)
            _state.value = reduce(_events.value)
                .copy(teamAName = currentTeamAName, teamBName = currentTeamBName)
            _fallOfWickets.value = computeFallOfWickets(_events.value)
            // Console state is not fully rolled back (other stats require a full replay),
            // but maiden counts are derived from the event log and can always be kept correct.
            refreshMaidensFromEvents(_events.value)
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
        } else {
            val current = _events.value
            if (globalIndex < 0 || globalIndex >= current.size) return
            _events.value = current.toMutableList().apply { set(globalIndex, updatedEvent) }
            _state.value = reduce(_events.value)
                .copy(teamAName = currentTeamAName, teamBName = currentTeamBName)
            _fallOfWickets.value = computeFallOfWickets(_events.value)
            refreshMaidensFromEvents(_events.value)
        }
    }

    /**
     * Delete the [BallEvent] at [globalIndex] from the active-innings event log
     * (or the first-innings log when [inFirstInnings] is true).
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
        } else {
            val current = _events.value
            if (globalIndex < 0 || globalIndex >= current.size) return
            _events.value = _events.value.filterIndexed { index, _ -> index != globalIndex }
            _state.value = reduce(_events.value)
                .copy(teamAName = currentTeamAName, teamBName = currentTeamBName)
            _fallOfWickets.value = computeFallOfWickets(_events.value)
            refreshMaidensFromEvents(_events.value)
        }
    }

    /**
     * Recompute the first-innings aggregate snapshot stored in [ScoringConsoleState] by
     * replaying [firstEvents] through the reducer.
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
            firstInningsBowlingEntries = updatedFirstBowling
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

    fun resetMatch() {
        _events.value = emptyList()
        _state.value = MatchState()
        _consoleState.value = ScoringConsoleState()
        _fallOfWickets.value = emptyList()
        _activeMatch.value = null
        _currentPartnership.value = null
        _completedPartnerships.value = emptyList()
        _firstInningsCompletedPartnerships.value = emptyList()
        currentTeamAName = "Team A"
        currentTeamBName = "Team B"
    }

    // ---------------------------------------------------------------------------
    // Player management
    // ---------------------------------------------------------------------------

    /** Called once when opening batters and the first bowler are confirmed. */
    fun setOpeners(striker: Player, nonStriker: Player, bowler: Player) {
        val strikerEntry = BattingEntry(player = striker)
        val nonStrikerEntry = BattingEntry(player = nonStriker)
        val bowlerEntry = BowlingEntry(player = bowler)
        val phase = if (_consoleState.value.inningsNumber == 1) InningsPhase.FIRST_INNINGS
                    else InningsPhase.SECOND_INNINGS
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
            bowlerChangePending = false
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
            bowlerChangePending = false
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
        val updatedTeam = targetTeam.copy(players = targetTeam.players + player)
        val updatedMatch = match.updateTeamRef(targetTeam, updatedTeam)
        _activeMatch.value = updatedMatch
        MatchRepository.updateMatch(updatedMatch)
    }

    // ---------------------------------------------------------------------------
    // Innings management
    // ---------------------------------------------------------------------------

    /**
     * End the first innings manually.
     * Saves the first-innings totals and scorecard snapshot, then enters the
     * [InningsPhase.INNINGS_BREAK] state so the UI can display the target
     * before the scorer explicitly starts the second innings.
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
            target = state.runs + 1
        )
        // Snapshot completed partnerships; also finalize any ongoing partnership.
        val ongoing = _currentPartnership.value?.copy(endScore = state.runs, isCurrent = false)
        _firstInningsCompletedPartnerships.value =
            _completedPartnerships.value + listOfNotNull(ongoing)
        _currentPartnership.value = null
        _completedPartnerships.value = emptyList()
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
            target = console.target
        )
    }

    /** Mark the match as complete. */
    fun endMatch() {
        _consoleState.value = _consoleState.value.copy(phase = InningsPhase.MATCH_COMPLETE)
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
        val hasPlayers = match.battingFirst.players.isNotEmpty() &&
                match.bowlingFirst.players.isNotEmpty()
        _consoleState.value = ScoringConsoleState(
            inningsNumber = 1,
            phase = if (hasPlayers) InningsPhase.SETUP else InningsPhase.FIRST_INNINGS,
            battingTeamName = match.battingFirst.name,
            bowlingTeamName = match.bowlingFirst.name
        )
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

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
        return battingTeam.players.filter { it.id !in dismissedIds && it.id !in currentIds }
    }

    private fun availableBowlers(): List<Player> {
        val match = _activeMatch.value ?: return emptyList()
        val console = _consoleState.value
        val bowlingTeam = if (console.inningsNumber == 1) match.bowlingFirst else match.battingFirst
        val lastBowlerId = console.currentBowler?.id
        // The same bowler cannot bowl consecutive overs
        return bowlingTeam.players.filter { it.id != lastBowlerId }
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
