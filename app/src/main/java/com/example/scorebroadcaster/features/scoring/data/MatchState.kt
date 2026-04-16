package com.example.scorebroadcaster.features.scoring.data

import com.example.scorebroadcaster.features.teams.data.Team
data class MatchState(
    val teamAName: String = "Team A",
    val teamBName: String = "Team B",
    val runs: Int = 0,
    val wickets: Int = 0,
    val overs: Int = 0,
    val balls: Int = 0,
    val lastBalls: List<String> = emptyList(),
    val extras: Int = 0,
    val wides: Int = 0,
    val noBalls: Int = 0,
    val byes: Int = 0,
    val legByes: Int = 0,
    /** Penalty runs awarded by the umpire (not counted as extras, not tied to a delivery). */
    val penaltyRuns: Int = 0,
    /**
     * True when the innings has consumed all its allocated overs (i.e. total legal balls
     * delivered equals [maxOvers] * 6).  Set by the scorer at domain/reducer level;
     * the UI uses this to disable scoring controls as a secondary guard.
     *
     * Always false when [maxOvers] is 0 (no limit configured, default for viewer or legacy state).
     */
    val isInningsOver: Boolean = false
)
