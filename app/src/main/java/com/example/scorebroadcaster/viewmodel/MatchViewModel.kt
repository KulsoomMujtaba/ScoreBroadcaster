package com.example.scorebroadcaster.viewmodel

import androidx.lifecycle.ViewModel
import com.example.scorebroadcaster.data.InningsPhase
import com.example.scorebroadcaster.data.MatchState
import com.example.scorebroadcaster.data.PendingAction
import com.example.scorebroadcaster.data.ScoreEvent
import com.example.scorebroadcaster.data.ScoringConsoleState
import com.example.scorebroadcaster.data.entity.BattingEntry
import com.example.scorebroadcaster.data.entity.BowlingEntry
import com.example.scorebroadcaster.data.entity.Match
import com.example.scorebroadcaster.data.entity.Player
import com.example.scorebroadcaster.domain.reduce
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MatchViewModel : ViewModel() {

    /** The match entity currently being scored. Null until [initFromMatch] is called. */
    private val _activeMatch = MutableStateFlow<Match?>(null)
    val activeMatch: StateFlow<Match?> = _activeMatch.asStateFlow()

    private val _events = MutableStateFlow<List<ScoreEvent>>(emptyList())

    private val _state = MutableStateFlow(MatchState())
    val state: StateFlow<MatchState> = _state.asStateFlow()

    private val _consoleState = MutableStateFlow(ScoringConsoleState())
    val consoleState: StateFlow<ScoringConsoleState> = _consoleState.asStateFlow()

    // Preserved team names so they survive repeated reduce() calls
    private var currentTeamAName = "Team A"
    private var currentTeamBName = "Team B"

    // ---------------------------------------------------------------------------
    // Core scoring
    // ---------------------------------------------------------------------------

    fun addEvent(event: ScoreEvent) {
        val prevState = _state.value
        _events.value = _events.value + event
        val newState = reduce(_events.value)
            .copy(teamAName = currentTeamAName, teamBName = currentTeamBName)
        _state.value = newState
        updateConsoleAfterEvent(event, prevState, newState)
    }

    private fun updateConsoleAfterEvent(
        event: ScoreEvent,
        prevState: MatchState,
        newState: MatchState
    ) {
        val console = _consoleState.value
        if (console.phase == InningsPhase.SETUP || console.phase == InningsPhase.MATCH_COMPLETE) return
        val striker = console.striker ?: return

        // --- Update striker batting entry ---
        val updatedStrikerEntry = console.strikerEntry?.let { entry ->
            when (event) {
                is ScoreEvent.Run -> entry.copy(
                    runs = entry.runs + event.runs,
                    balls = entry.balls + 1,
                    fours = if (event.runs == 4) entry.fours + 1 else entry.fours,
                    sixes = if (event.runs == 6) entry.sixes + 1 else entry.sixes
                )
                is ScoreEvent.Wicket -> entry.copy(balls = entry.balls + 1, isOut = true)
                is ScoreEvent.NoBall -> entry.copy(runs = entry.runs + event.runs)
                is ScoreEvent.Bye, is ScoreEvent.LegBye -> entry.copy(balls = entry.balls + 1)
                else -> entry // Wide: no batter-ball count change
            }
        }

        // --- Update current bowler entry ---
        val updatedBowlerEntry = console.currentBowlerEntry?.let { entry ->
            when (event) {
                is ScoreEvent.Wide -> entry.copy(runs = entry.runs + event.runs + 1)
                is ScoreEvent.NoBall -> entry.copy(runs = entry.runs + event.runs + 1)
                is ScoreEvent.Run -> {
                    val (o, b) = incrementBall(entry.overs, entry.balls)
                    entry.copy(runs = entry.runs + event.runs, overs = o, balls = b)
                }
                is ScoreEvent.Wicket -> {
                    val (o, b) = incrementBall(entry.overs, entry.balls)
                    entry.copy(wickets = entry.wickets + 1, overs = o, balls = b)
                }
                is ScoreEvent.Bye -> {
                    val (o, b) = incrementBall(entry.overs, entry.balls)
                    entry.copy(overs = o, balls = b) // byes do not count against bowler
                }
                is ScoreEvent.LegBye -> {
                    val (o, b) = incrementBall(entry.overs, entry.balls)
                    entry.copy(overs = o, balls = b) // leg byes do not count against bowler
                }
            }
        }

        // --- Propagate to aggregate lists ---
        val updatedAllBatting = if (updatedStrikerEntry != null) {
            console.allBattingEntries.map {
                if (it.player.id == striker.id) updatedStrikerEntry else it
            }
        } else console.allBattingEntries

        val updatedAllBowling = if (updatedBowlerEntry != null && console.currentBowler != null) {
            console.allBowlingEntries.map {
                if (it.player.id == console.currentBowler.id) updatedBowlerEntry else it
            }
        } else console.allBowlingEntries

        // --- Detect over end (a legal ball that completes an over) ---
        val isLegalBall = event !is ScoreEvent.Wide && event !is ScoreEvent.NoBall
        val overEnded = isLegalBall && newState.balls == 0 && newState.overs > prevState.overs

        val wicketFell = event is ScoreEvent.Wicket

        // --- Strike rotation ---
        val oddRuns = when (event) {
            is ScoreEvent.Run -> event.runs % 2 == 1
            is ScoreEvent.Bye -> event.runs % 2 == 1
            is ScoreEvent.LegBye -> event.runs % 2 == 1
            else -> false
        }
        // Strike rotation rules:
        //  - Wicket: new batter always comes in at striker's position (null = waiting for selection).
        //  - Over end + odd runs: rotations cancel each other out (no net change).
        //  - Over end OR odd runs (not both): rotate striker and non-striker.
        val (rotatedStriker, rotatedNonStriker) = when {
            wicketFell -> Pair(null, console.nonStriker)
            overEnded && oddRuns -> Pair(console.striker, console.nonStriker)
            overEnded || oddRuns -> Pair(console.nonStriker, console.striker)
            else -> Pair(console.striker, console.nonStriker)
        }

        // --- Determine pending action ---
        val (pendingAction, bowlerChangePending) = when {
            wicketFell -> {
                val remaining = availableBatters()
                if (remaining.isNotEmpty()) {
                    Pair(PendingAction.SelectNextBatter(remaining), overEnded)
                } else {
                    // All out — no pending action; innings ends naturally
                    Pair(null, false)
                }
            }
            overEnded -> Pair(PendingAction.SelectBowler(availableBowlers()), false)
            else -> Pair(null, false)
        }

        _consoleState.value = console.copy(
            striker = rotatedStriker,
            nonStriker = rotatedNonStriker,
            strikerEntry = updatedAllBatting.find { it.player.id == rotatedStriker?.id },
            nonStrikerEntry = updatedAllBatting.find { it.player.id == rotatedNonStriker?.id },
            currentBowlerEntry = updatedBowlerEntry,
            allBattingEntries = updatedAllBatting,
            allBowlingEntries = updatedAllBowling,
            pendingAction = pendingAction,
            bowlerChangePending = bowlerChangePending
        )
    }

    fun undo() {
        if (_events.value.isNotEmpty()) {
            _events.value = _events.value.dropLast(1)
            _state.value = reduce(_events.value)
                .copy(teamAName = currentTeamAName, teamBName = currentTeamBName)
            // Console state is not rolled back for simplicity; scorer can re-select if needed.
        }
    }

    fun resetMatch() {
        _events.value = emptyList()
        _state.value = MatchState()
        _consoleState.value = ScoringConsoleState()
        _activeMatch.value = null
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
    }

    /** Called after a wicket when the scorer picks the incoming batter. */
    fun selectNextBatter(player: Player) {
        val console = _consoleState.value
        val newEntry = BattingEntry(player = player)
        val updatedAll = console.allBattingEntries + newEntry
        // If the over also ended when the wicket fell, chain into a bowler-change dialog.
        val nextPending = if (console.bowlerChangePending) {
            PendingAction.SelectBowler(availableBowlers())
        } else null
        _consoleState.value = console.copy(
            striker = player,
            strikerEntry = newEntry,
            allBattingEntries = updatedAll,
            pendingAction = nextPending,
            bowlerChangePending = false
        )
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
        _consoleState.value = console.copy(
            phase = InningsPhase.INNINGS_BREAK,
            firstInningsRuns = state.runs,
            firstInningsWickets = state.wickets,
            firstInningsExtras = state.extras,
            firstInningsBattingEntries = console.allBattingEntries,
            firstInningsBowlingEntries = console.allBowlingEntries,
            target = state.runs + 1
        )
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
            firstInningsBattingEntries = console.firstInningsBattingEntries,
            firstInningsBowlingEntries = console.firstInningsBowlingEntries,
            target = console.target
        )
    }

    /** Mark the match as complete. */
    fun endMatch() {
        _consoleState.value = _consoleState.value.copy(phase = InningsPhase.MATCH_COMPLETE)
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

    private fun availableBatters(): List<Player> {
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
}
