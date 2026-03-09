package com.example.scorebroadcaster.ui

import com.example.scorebroadcaster.data.InningsPhase
import com.example.scorebroadcaster.data.MatchState
import com.example.scorebroadcaster.data.ScoringConsoleState

/** Batter information for the broadcast overlay. */
data class BatterOverlayInfo(
    val name: String,
    val runs: Int,
    val balls: Int,
    val isStriker: Boolean
)

/** Bowler information for the broadcast overlay. */
data class BowlerOverlayInfo(
    val name: String,
    val wickets: Int,
    val runs: Int
)

/**
 * Fully resolved UI model for the TV-style broadcast lower-third overlay.
 *
 * Produced by [BroadcastOverlayMapper.map] from [MatchState] + [ScoringConsoleState].
 * A null result from the mapper means "don't show the overlay" (e.g. during [InningsPhase.SETUP]).
 */
data class BroadcastOverlayModel(
    /** Short match title, e.g. "LIO v FAL". */
    val matchTitle: String,
    /** Score string, e.g. "177-2". */
    val score: String,
    /** Overs string, e.g. "28.5 overs". */
    val overs: String,
    /** Innings badge, e.g. "1st" or "2nd". */
    val inningsBadge: String,
    /** Striker info; null if no striker is set. */
    val striker: BatterOverlayInfo?,
    /** Non-striker info; null if no non-striker is set. */
    val nonStriker: BatterOverlayInfo?,
    /** Current bowler info; null if no bowler is set. */
    val bowler: BowlerOverlayInfo?,
    /** Last-ball labels for the current over (from [MatchState.lastBalls]). */
    val currentOverBalls: List<String>,
    /** Optional context line, e.g. "RUN RATE 6.21" or "Need 23 from 15". */
    val contextLine: String?
)

/**
 * Converts a [MatchState] + [ScoringConsoleState] pair into a [BroadcastOverlayModel].
 *
 * Returns null during [InningsPhase.SETUP] so the overlay can be hidden before play starts.
 *
 * @param match      Live match state from the scoring engine.
 * @param console    Live console state with player and innings data.
 * @param matchOvers Total overs for the match (from [com.example.scorebroadcaster.data.entity.Match.overs]),
 *                   used to compute balls remaining in the second innings. Null disables that detail.
 */
object BroadcastOverlayMapper {

    fun map(
        match: MatchState,
        console: ScoringConsoleState,
        matchOvers: Int? = null
    ): BroadcastOverlayModel? {
        if (console.phase == InningsPhase.SETUP) return null

        val inningsBadge = if (console.inningsNumber == 1) "1st" else "2nd"

        val striker = console.strikerEntry?.let {
            BatterOverlayInfo(it.player.name, it.runs, it.balls, isStriker = true)
        }
        val nonStriker = console.nonStrikerEntry?.let {
            BatterOverlayInfo(it.player.name, it.runs, it.balls, isStriker = false)
        }
        val bowler = console.currentBowlerEntry?.let {
            BowlerOverlayInfo(it.player.name, it.wickets, it.runs)
        }

        return BroadcastOverlayModel(
            matchTitle = "${console.battingTeamName} v ${console.bowlingTeamName}",
            score = "${match.runs}-${match.wickets}",
            overs = "${match.overs}.${match.balls} overs",
            inningsBadge = inningsBadge,
            striker = striker,
            nonStriker = nonStriker,
            bowler = bowler,
            currentOverBalls = match.lastBalls,
            contextLine = buildContextLine(match, console, matchOvers)
        )
    }

    private fun buildContextLine(
        match: MatchState,
        console: ScoringConsoleState,
        matchOvers: Int?
    ): String? {
        val totalBalls = match.overs * 6 + match.balls
        return when (console.phase) {
            InningsPhase.FIRST_INNINGS -> {
                if (totalBalls > 0) {
                    val rr = match.runs.toFloat() / (totalBalls.toFloat() / 6f)
                    "RUN RATE %.2f".format(rr)
                } else null
            }
            InningsPhase.SECOND_INNINGS -> {
                val runsNeeded = console.target - match.runs
                if (runsNeeded > 0) {
                    val ballsRemaining = matchOvers?.let { it * 6 - totalBalls }
                    if (ballsRemaining != null && ballsRemaining > 0) {
                        "Need $runsNeeded from $ballsRemaining"
                    } else {
                        "Need $runsNeeded"
                    }
                } else null
            }
            InningsPhase.INNINGS_BREAK -> "Target: ${console.target}"
            InningsPhase.MATCH_COMPLETE -> {
                if (console.target > 0) {
                    if (match.runs >= console.target) "${console.battingTeamName} won"
                    else "${console.bowlingTeamName} won"
                } else null
            }
            else -> null
        }
    }
}
