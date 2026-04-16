package com.devhub.scored.features.match.data

import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val TAG = "SupabaseMatch"

/** UUID v4 regex used for defensive validation before Supabase insert. */
private val UUID_REGEX = Regex(
    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
    RegexOption.IGNORE_CASE
)

/** Returns `true` if this string is a well-formed UUID. */
private fun String.isValidUuid(): Boolean = UUID_REGEX.matches(this)

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
 * [teamAId] and [teamBId] use the team's UUID ([Team.id]) so that the Supabase
 * `team_a_id` / `team_b_id` UUID columns receive a valid UUID value.  Display
 * names must not be sent to UUID columns — they are available via [Match.displayTitle].
 *
 * [tossWinnerTeamId] is likewise stored as the winning team's UUID.
 *
 * Logs a warning if any UUID field fails basic format validation before the insert.
 */
fun Match.toSupabaseMatch(userId: String): SupabaseMatch {
    Log.d(TAG, "Inserting match with teamAId=${teamA.id} teamBId=${teamB.id} tossWinnerId=${tossWinner.id}")
    if (!teamA.id.isValidUuid()) Log.w(TAG, "teamA.id is not a valid UUID: ${teamA.id}")
    if (!teamB.id.isValidUuid()) Log.w(TAG, "teamB.id is not a valid UUID: ${teamB.id}")
    if (!tossWinner.id.isValidUuid()) Log.w(TAG, "tossWinner.id is not a valid UUID: ${tossWinner.id}")
    return SupabaseMatch(
        id = localId,
        userId = userId,
        teamAId = teamA.id,
        teamBId = teamB.id,
        matchName = displayTitle,
        format = format.name,
        totalOvers = overs,
        tossWinnerTeamId = tossWinner.id,
        tossDecision = tossDecision.name,
        status = status.name,
        isPublished = visibility == MatchVisibility.PUBLISHED,
        shareCode = shareCode
    )
}

/** Convert a remote [SupabaseMatch] back to a local [Match] domain model.
 *
 * Supports two formats stored in [teamAId] / [teamBId]:
 * - **New format** (UUID): The field is a proper UUID.  Team names are recovered from
 *   [matchName] when it follows the "TeamA vs TeamB" convention, otherwise the UUID is
 *   used as a placeholder name.
 * - **Old format** (name-as-ID): The field contains a plain team name (written by
 *   earlier app versions).  In this case the name is used directly and a fresh UUID is
 *   generated for the local [Team.id].
 */
fun SupabaseMatch.toMatch(): Match {
    // Attempt to recover team names from matchName ("TeamA vs TeamB").
    val parts = matchName.split(" vs ", limit = 2)
    val derivedNameA = parts.getOrNull(0)?.takeIf { it.isNotBlank() }
    val derivedNameB = parts.getOrNull(1)?.takeIf { it.isNotBlank() }

    val teamA: com.devhub.scored.features.teams.data.Team
    val teamB: com.devhub.scored.features.teams.data.Team
    if (teamAId.isValidUuid() && teamBId.isValidUuid()) {
        // New format: teamAId/teamBId are proper UUIDs — use them as Team.id.
        teamA = com.devhub.scored.features.teams.data.Team(
            id = teamAId,
            name = derivedNameA ?: teamAId
        )
        teamB = com.devhub.scored.features.teams.data.Team(
            id = teamBId,
            name = derivedNameB ?: teamBId
        )
    } else {
        // Old format: teamAId/teamBId contain team names — use them as display names.
        teamA = com.devhub.scored.features.teams.data.Team(name = teamAId)
        teamB = com.devhub.scored.features.teams.data.Team(name = teamBId)
    }
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
