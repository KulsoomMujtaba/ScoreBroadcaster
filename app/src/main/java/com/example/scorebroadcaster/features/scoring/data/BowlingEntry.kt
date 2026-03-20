package com.example.scorebroadcaster.features.scoring.data

import com.example.scorebroadcaster.features.players.data.Player

data class BowlingEntry(
    val player: Player,
    val overs: Int = 0,
    val balls: Int = 0,
    val runs: Int = 0,
    val wickets: Int = 0,
    val maidens: Int = 0
)
