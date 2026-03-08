package com.example.scorebroadcaster.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.scorebroadcaster.data.entity.PlayerProfile
import com.example.scorebroadcaster.data.entity.PlayerSourceType

/**
 * Room entity for a reusable player profile stored locally.
 *
 * Mirrors [com.example.scorebroadcaster.data.entity.PlayerProfile].
 * Use [toDomain] / [PlayerProfile.toEntity] to convert between the two.
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

/** Convert a Room entity to the domain model. */
fun PlayerProfileEntity.toDomain(): PlayerProfile = PlayerProfile(
    id = id,
    displayName = displayName,
    playerSourceType = PlayerSourceType.valueOf(playerSourceType),
    linkedUserId = linkedUserId,
    avatarUrl = avatarUrl,
    role = role,
    battingStyle = battingStyle,
    bowlingStyle = bowlingStyle
)

/** Convert a domain model to a Room entity. */
fun PlayerProfile.toEntity(): PlayerProfileEntity = PlayerProfileEntity(
    id = id,
    displayName = displayName,
    playerSourceType = playerSourceType.name,
    linkedUserId = linkedUserId,
    avatarUrl = avatarUrl,
    role = role,
    battingStyle = battingStyle,
    bowlingStyle = bowlingStyle
)
