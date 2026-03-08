package com.example.scorebroadcaster.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for a reusable player profile stored locally.
 *
 * Mirrors [com.example.scorebroadcaster.data.entity.PlayerProfile].
 * The in-memory [com.example.scorebroadcaster.repository.SavedPlayerRepository]
 * continues to be the active repository until migration in a future phase.
 */
@Entity(tableName = "player_profiles")
data class PlayerProfileEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val playerSourceType: String,
    val linkedUserId: String?,
    val avatarUrl: String?,
    val role: String?,
    val battingStyle: String?,
    val bowlingStyle: String?
)
