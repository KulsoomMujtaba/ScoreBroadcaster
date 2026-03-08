package com.example.scorebroadcaster.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.scorebroadcaster.data.entity.Match
import com.example.scorebroadcaster.data.entity.MatchFormat
import com.example.scorebroadcaster.data.entity.MatchStatus
import com.example.scorebroadcaster.data.entity.MatchVisibility
import com.example.scorebroadcaster.data.entity.Team
import com.example.scorebroadcaster.data.entity.TossDecision

/**
 * Room entity for a cricket match stored locally.
 *
 * Persists match metadata only. Complex nested objects (teams with players,
 * toss details, innings) are not stored in this phase; [toDomain] reconstructs
 * them with placeholder values sufficient for the match list UI.
 *
 * [MatchFormat], [MatchStatus], and [MatchVisibility] are stored as their
 * enum name strings.
 */
@Entity(tableName = "matches")
data class MatchEntity(
    @PrimaryKey val localId: String,
    val title: String?,
    val teamAName: String,
    val teamBName: String,
    val format: String,
    val overs: Int,
    val status: String,
    val createdAt: Long,
    val visibility: String,
    val ownerUserId: String?,
    val remoteId: String?,
    val publishedAt: Long?,
    val shareCode: String?
)

/**
 * Reconstructs a [Match] from persisted metadata.
 *
 * Fields not stored in this phase (tossWinner, tossDecision, battingFirst,
 * bowlingFirst, innings) are given sensible placeholder values. These are
 * sufficient for the match list UI; a full match state restore is out of
 * scope until ball events are also persisted.
 */
fun MatchEntity.toDomain(): Match {
    val teamA = Team(name = teamAName)
    val teamB = Team(name = teamBName)
    return Match(
        id = localId,
        localId = localId,
        title = title ?: "",  // Match.title is non-nullable; empty string matches the domain default
        teamA = teamA,
        teamB = teamB,
        format = runCatching { MatchFormat.valueOf(format) }.getOrDefault(MatchFormat.CUSTOM),
        overs = overs,
        tossWinner = teamA,
        tossDecision = TossDecision.BAT,
        battingFirst = teamA,
        bowlingFirst = teamB,
        status = runCatching { MatchStatus.valueOf(status) }.getOrDefault(MatchStatus.NOT_STARTED),
        createdAt = createdAt,
        visibility = runCatching { MatchVisibility.valueOf(visibility) }.getOrDefault(MatchVisibility.PRIVATE),
        ownerUserId = ownerUserId,
        remoteId = remoteId,
        publishedAt = publishedAt,
        shareCode = shareCode
    )
}

fun Match.toEntity(): MatchEntity = MatchEntity(
    localId = localId,
    title = title.ifBlank { null },
    teamAName = teamA.name,
    teamBName = teamB.name,
    format = format.name,
    overs = overs,
    status = status.name,
    createdAt = createdAt,
    visibility = visibility.name,
    ownerUserId = ownerUserId,
    remoteId = remoteId,
    publishedAt = publishedAt,
    shareCode = shareCode
)
