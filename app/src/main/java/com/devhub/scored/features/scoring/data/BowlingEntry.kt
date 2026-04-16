package com.devhub.scored.features.scoring.data

import com.devhub.scored.features.players.data.Player

data class BowlingEntry(
    val player: Player,
    val overs: Int = 0,
    val balls: Int = 0,
    val runs: Int = 0,
    val wickets: Int = 0,
    val maidens: Int = 0
)
