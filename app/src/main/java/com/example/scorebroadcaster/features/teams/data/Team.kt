package com.example.scorebroadcaster.features.teams.data

import com.example.scorebroadcaster.features.players.data.Player
import java.util.UUID

data class Team(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val players: List<Player> = emptyList()
)
