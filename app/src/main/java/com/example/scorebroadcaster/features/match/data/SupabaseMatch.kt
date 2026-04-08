package com.example.scorebroadcaster.features.match.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Remote representation of a match stored in Supabase.
 *
 * Maps to the `matches` table with columns matching the field names below.
 * The [id] is the same UUID used locally ([Match.localId]) so the same
 * identifier is shared across local Room storage and the remote `matches` table.
 *
 * Only metadata is stored here — ball-by-ball events are not synced (see FUTURE CONTEXT).
 */
@Serializable
data class SupabaseMatch(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("team_a_id") val teamAId: String,
    @SerialName("team_b_id") val teamBId: String,
    @SerialName("match_name") val matchName: String,
    val format: String,
    @SerialName("total_overs") val totalOvers: Int,
    @SerialName("toss_winner_team_id") val tossWinnerTeamId: String,
    @SerialName("toss_decision") val tossDecision: String,
    val status: String,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = ""
)

/**
 * Convert a local [Match] to its remote [SupabaseMatch] representation.
 *
 * [teamAId] and [teamBId] use the team names as stable identifiers because
 * ad-hoc teams created during match setup do not have persisted Room UUIDs.
 * When saved teams are linked to a match in a future phase, these fields
 * should be replaced with the actual [SavedTeam.id] values.
 *
 * [tossWinnerTeamId] is stored as the winning team's name for the same reason.
 */
fun Match.toSupabaseMatch(userId: String): SupabaseMatch = SupabaseMatch(
    id = localId,
    userId = userId,
    teamAId = teamA.name,
    teamBId = teamB.name,
    matchName = displayTitle,
    format = format.name,
    totalOvers = overs,
    tossWinnerTeamId = tossWinner.name,
    tossDecision = tossDecision.name,
    status = status.name
)

/** Convert a remote [SupabaseMatch] back to a local [Match] domain model. */
fun SupabaseMatch.toMatch(): Match {
    val teamA = com.example.scorebroadcaster.features.teams.data.Team(name = teamAId)
    val teamB = com.example.scorebroadcaster.features.teams.data.Team(name = teamBId)
    val tossWinnerTeam = if (tossWinnerTeamId == teamAId) teamA else teamB
    val tossDecisionEnum = runCatching { TossDecision.valueOf(tossDecision) }.getOrDefault(TossDecision.BAT)
    val battingFirst = if (tossDecisionEnum == TossDecision.BAT) tossWinnerTeam
                       else if (tossWinnerTeam == teamA) teamB else teamA
    val bowlingFirst = if (battingFirst == teamA) teamB else teamA
    return Match(
        id = id,
        localId = id,
        title = matchName,
        teamA = teamA,
        teamB = teamB,
        format = runCatching { MatchFormat.valueOf(format) }.getOrDefault(MatchFormat.CUSTOM),
        overs = totalOvers,
        tossWinner = tossWinnerTeam,
        tossDecision = tossDecisionEnum,
        battingFirst = battingFirst,
        bowlingFirst = bowlingFirst,
        status = runCatching { MatchStatus.valueOf(status) }.getOrDefault(MatchStatus.NOT_STARTED),
        ownerUserId = userId
    )
}
