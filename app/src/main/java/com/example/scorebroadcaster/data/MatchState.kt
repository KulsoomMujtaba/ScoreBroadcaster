package com.example.scorebroadcaster.data

data class MatchState(
    val teamAName: String = "Team A",
    val teamBName: String = "Team B",
    val runs: Int = 0,
    val wickets: Int = 0,
    val overs: Int = 0,
    val balls: Int = 0,
    val lastBalls: List<String> = emptyList(),
    val extras: Int = 0
)
