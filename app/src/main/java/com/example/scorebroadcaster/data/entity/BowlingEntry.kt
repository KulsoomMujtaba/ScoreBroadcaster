package com.example.scorebroadcaster.data.entity

data class BowlingEntry(
    val player: Player,
    val overs: Int = 0,
    val balls: Int = 0,
    val runs: Int = 0,
    val wickets: Int = 0,
    val maidens: Int = 0
)
