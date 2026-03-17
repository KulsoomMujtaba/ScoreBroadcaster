package com.example.scorebroadcaster.features.teams.data
import androidx.room.Entity

/**
 * Junction table linking a [SavedTeamEntity] to the [PlayerProfileEntity] records
 * that belong to it.
 *
 * Relations (many-to-many) will be implemented in a future phase.
 */
@Entity(
    tableName = "saved_team_player_cross_ref",
    primaryKeys = ["teamId", "playerProfileId"]
)
data class SavedTeamPlayerCrossRef(
    val teamId: String,
    val playerProfileId: String
)
