package com.example.scorebroadcaster.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for a cricket match stored locally.
 *
 * Mirrors the publish-ready fields of [com.example.scorebroadcaster.data.entity.Match].
 * The in-memory [com.example.scorebroadcaster.repository.MatchRepository]
 * continues to be the active repository until migration in a future phase.
 *
 * Complex nested objects (teams, innings) will be represented by separate
 * entities and relations in a future phase.
 */
@Entity(tableName = "matches")
data class MatchEntity(
    @PrimaryKey val localId: String,
    val title: String,
    val teamAName: String,
    val teamBName: String,
    val format: String,
    val overs: Int,
    val status: String,
    val createdAt: Long,
    val visibility: String,
    val ownerUserId: String?,
    val shareCode: String?
)
