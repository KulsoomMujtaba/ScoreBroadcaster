package com.example.scorebroadcaster.data.entity

data class Innings(
    val number: Int,
    val battingTeam: Team,
    val bowlingTeam: Team,
    val batting: List<BattingEntry> = emptyList(),
    val bowling: List<BowlingEntry> = emptyList(),
    val totalRuns: Int = 0,
    val wickets: Int = 0,
    val overs: Int = 0,
    val balls: Int = 0,
    val extras: Int = 0,
    val isCompleted: Boolean = false
)
