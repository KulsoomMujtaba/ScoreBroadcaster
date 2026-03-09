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
 * Persists all metadata required to fully rebuild the [Match] domain object,
 * including toss details and the overs limit.
 *
 * [MatchFormat], [MatchStatus], [MatchVisibility], and [TossDecision] are stored
 * as their enum name strings. [tossWinner] stores the winning team's name and is
 * matched against [teamAName] / [teamBName] on read.
 */
@Entity(tableName = "matches")
data class MatchEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val ownerUserId: String?,
    val visibility: String,

    val title: String?,
    val format: String,
    val oversLimit: Int,

    val teamAName: String,
    val teamBName: String,

    val tossWinner: String,
    val tossDecision: String,

    val status: String,

    val createdAt: Long,
    val publishedAt: Long?,
    val shareCode: String?
)

/**
 * Reconstructs a [Match] from persisted metadata.
 *
 * [tossWinner] is matched by name against [teamAName] / [teamBName] to obtain
 * the correct [Team] reference. [battingFirst] and [bowlingFirst] are derived
 * from the persisted [tossDecision].
 */
fun MatchEntity.toDomain(): Match {
    val teamA = Team(name = teamAName)
    val teamB = Team(name = teamBName)
    // Default to teamB when the stored name matches neither — handles corrupt data gracefully.
    val tossWinnerTeam = if (tossWinner == teamAName) teamA else teamB
    val tossDecisionEnum = runCatching { TossDecision.valueOf(tossDecision) }.getOrDefault(TossDecision.BAT)
    val battingFirst = if (tossDecisionEnum == TossDecision.BAT) tossWinnerTeam
                       else if (tossWinnerTeam == teamA) teamB else teamA
    val bowlingFirst = if (battingFirst == teamA) teamB else teamA
    return Match(
        id = localId,
        localId = localId,
        title = title ?: "",
        teamA = teamA,
        teamB = teamB,
        format = runCatching { MatchFormat.valueOf(format) }.getOrDefault(MatchFormat.CUSTOM),
        overs = oversLimit,
        tossWinner = tossWinnerTeam,
        tossDecision = tossDecisionEnum,
        battingFirst = battingFirst,
        bowlingFirst = bowlingFirst,
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
    remoteId = remoteId,
    ownerUserId = ownerUserId,
    visibility = visibility.name,
    title = title.ifBlank { null },
    format = format.name,
    oversLimit = overs,
    teamAName = teamA.name,
    teamBName = teamB.name,
    tossWinner = tossWinner.name,
    tossDecision = tossDecision.name,
    status = status.name,
    createdAt = createdAt,
    publishedAt = publishedAt,
    shareCode = shareCode
)
