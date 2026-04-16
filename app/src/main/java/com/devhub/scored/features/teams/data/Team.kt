package com.devhub.scored.features.teams.data

import com.devhub.scored.features.players.data.Player
import java.util.UUID

data class Team(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val players: List<Player> = emptyList()
)
