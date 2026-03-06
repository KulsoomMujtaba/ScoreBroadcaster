package com.example.scorebroadcaster.data.entity

import java.util.UUID

/**
 * A reusable team template that can be loaded into a match during Create Match flow.
 * Players are copied into the match context so each match remains independent.
 */
data class SavedTeam(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val players: List<Player> = emptyList()
)
