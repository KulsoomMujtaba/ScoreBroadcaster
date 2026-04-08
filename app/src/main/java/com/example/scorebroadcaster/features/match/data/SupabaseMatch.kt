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
    @SerialName("is_published") val isPublished: Boolean = false,
    @SerialName("share_code") val shareCode: String? = null,
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
    status = status.name,
    isPublished = visibility == MatchVisibility.PUBLISHED,
    shareCode = shareCode
)

/** Convert a remote [SupabaseMatch] back to a local [Match] domain model. */
fun SupabaseMatch.toMatch(): Match {
    val teamA = com.example.scorebroadcaster.features.teams.data.Team(name = teamAId)
    val teamB = com.example.scorebroadcaster.features.teams.data.Team(name = teamBId)
    val tossWinnerTeam = if (tossWinnerTeamId == teamAId) teamA else teamB
    val tossDecisionEnum = runCatching { TossDecision.valueOf(tossDecision) }
        // BAT is the safe fallback: a batsman-first assumption is less disruptive than BOWL
        // if the stored value is somehow invalid (e.g. schema mismatch).
        .getOrDefault(TossDecision.BAT)
    val battingFirst = if (tossDecisionEnum == TossDecision.BAT) tossWinnerTeam
                       else if (tossWinnerTeam == teamA) teamB else teamA
    val bowlingFirst = if (battingFirst == teamA) teamB else teamA
    val resolvedVisibility = when {
        isPublished -> MatchVisibility.PUBLISHED
        shareCode != null -> MatchVisibility.UNLISTED
        else -> MatchVisibility.PRIVATE
    }
    return Match(
        id = id,
        localId = id,
        title = matchName,
        teamA = teamA,
        teamB = teamB,
        format = runCatching { MatchFormat.valueOf(format) }
            // CUSTOM is the safest fallback — it imposes no overs constraint and allows
            // the match to be resumed without incorrect limits if the format name is unrecognised.
            .getOrDefault(MatchFormat.CUSTOM),
        overs = totalOvers,
        tossWinner = tossWinnerTeam,
        tossDecision = tossDecisionEnum,
        battingFirst = battingFirst,
        bowlingFirst = bowlingFirst,
        status = runCatching { MatchStatus.valueOf(status) }
            // NOT_STARTED is the safest fallback — it prevents the match from appearing
            // as resumable or completed if the stored status value is unrecognised.
            .getOrDefault(MatchStatus.NOT_STARTED),
        ownerUserId = userId,
        visibility = resolvedVisibility,
        shareCode = shareCode
    )
}
