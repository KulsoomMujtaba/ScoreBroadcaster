package com.example.scorebroadcaster.data

import com.example.scorebroadcaster.data.entity.BattingEntry
import com.example.scorebroadcaster.data.entity.BowlingEntry
import com.example.scorebroadcaster.data.entity.Player

/** Phase of the match scoring session. */
enum class InningsPhase {
    /** Waiting for the scorer to choose opening batters and bowler. */
    SETUP,
    /** First innings is in progress. */
    FIRST_INNINGS,
    /** Between innings: showing target before second innings starts. */
    INNINGS_BREAK,
    /** Second innings is in progress. */
    SECOND_INNINGS,
    /** Match has been completed. */
    MATCH_COMPLETE
}

/** A single scorer action that requires explicit input before play can resume. */
sealed class PendingAction {
    /** Prompt the scorer to pick the next bowler (triggered at end of each over). */
    data class SelectBowler(val availablePlayers: List<Player>) : PendingAction()

    /**
     * Prompt the scorer to pick the next batter (triggered after a wicket).
     *
     * @param replacingStriker true when the striker was dismissed (new batter comes in at
     *   striker's end); false when the non-striker was dismissed (e.g. run out).
     */
    data class SelectNextBatter(
        val availablePlayers: List<Player>,
        val replacingStriker: Boolean = true
    ) : PendingAction()
}

/**
 * Higher-level scoring-console state that complements [MatchState].
 *
 * [MatchState] tracks raw cumulative totals derived by folding [BallEvent]s
 * through the pure reducer. [ScoringConsoleState] tracks everything the
 * scoring console needs on top of that: which players are at the crease,
 * per-player batting/bowling stats, innings phase, and any pending dialog.
 */
data class ScoringConsoleState(
    val inningsNumber: Int = 1,
    val phase: InningsPhase = InningsPhase.SETUP,
    val battingTeamName: String = "Team A",
    val bowlingTeamName: String = "Team B",

    // Current players
    val striker: Player? = null,
    val nonStriker: Player? = null,
    val currentBowler: Player? = null,

    // Live stat entries for display
    val strikerEntry: BattingEntry? = null,
    val nonStrikerEntry: BattingEntry? = null,
    val currentBowlerEntry: BowlingEntry? = null,

    // Full innings scorecard
    val allBattingEntries: List<BattingEntry> = emptyList(),
    val allBowlingEntries: List<BowlingEntry> = emptyList(),

    // First-innings scorecard snapshot (saved when first innings ends)
    val firstInningsBattingEntries: List<BattingEntry> = emptyList(),
    val firstInningsBowlingEntries: List<BowlingEntry> = emptyList(),
    val firstInningsExtras: Int = 0,
    val firstInningsWides: Int = 0,
    val firstInningsNoBalls: Int = 0,
    val firstInningsByes: Int = 0,
    val firstInningsLegByes: Int = 0,
    val firstInningsOvers: Int = 0,
    val firstInningsBalls: Int = 0,

    // First-innings totals (populated at end of 1st innings; used for target in 2nd)
    val firstInningsRuns: Int = 0,
    val firstInningsWickets: Int = 0,

    /** Target runs for second innings (firstInningsRuns + 1). */
    val target: Int = 0,

    /** Action waiting for scorer input before the next ball can be bowled. */
    val pendingAction: PendingAction? = null,

    /**
     * True when the most recent wicket also ended an over, so a bowler-change
     * dialog must follow immediately after the next-batter dialog is resolved.
     */
    val bowlerChangePending: Boolean = false
)
