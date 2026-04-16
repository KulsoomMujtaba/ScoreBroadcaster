package com.devhub.scored.features.teams.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Remote representation of a team–player association stored in Supabase.
 *
 * Maps to the `team_players` join table with columns: id, team_id, player_id.
 *
 * The [playerId] corresponds to [com.devhub.scored.features.players.data.PlayerProfile.id]
 * (i.e. the [com.devhub.scored.features.players.data.Player.sourceProfileId] of the
 * local [com.devhub.scored.features.players.data.Player] snapshot).
 *
 * Only players with a non-null [com.devhub.scored.features.players.data.Player.sourceProfileId]
 * are stored in this table; ad-hoc (name-only) players are excluded from remote sync.
 */
@Serializable
data class SupabaseTeamPlayer(
    val id: String,
    @SerialName("team_id") val teamId: String,
    @SerialName("player_id") val playerId: String
)
