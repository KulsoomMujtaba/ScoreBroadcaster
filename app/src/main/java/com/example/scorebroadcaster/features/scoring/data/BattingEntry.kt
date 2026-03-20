package com.example.scorebroadcaster.features.scoring.data

import com.example.scorebroadcaster.features.players.data.Player

data class BattingEntry(
    val player: Player,
    val runs: Int = 0,
    val balls: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0,
    val isOut: Boolean = false,
    val dismissal: DismissalDetail? = null
)
