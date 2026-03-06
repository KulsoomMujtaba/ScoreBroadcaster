package com.example.scorebroadcaster.data.entity

import java.util.UUID

data class Player(
    val id: String = UUID.randomUUID().toString(),
    val name: String
)
