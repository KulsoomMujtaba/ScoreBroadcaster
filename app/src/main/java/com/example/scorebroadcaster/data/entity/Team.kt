package com.example.scorebroadcaster.data.entity

import java.util.UUID

data class Team(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val players: List<Player> = emptyList()
)
