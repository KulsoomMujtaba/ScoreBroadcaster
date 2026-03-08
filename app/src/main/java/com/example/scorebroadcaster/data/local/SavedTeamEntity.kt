package com.example.scorebroadcaster.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for a reusable saved team template stored locally.
 *
 * Mirrors [com.example.scorebroadcaster.data.entity.SavedTeam].
 * The in-memory [com.example.scorebroadcaster.repository.SavedTeamRepository]
 * continues to be the active repository until migration in a future phase.
 *
 * Players belonging to this team are tracked via [SavedTeamPlayerCrossRef].
 */
@Entity(tableName = "saved_teams")
data class SavedTeamEntity(
    @PrimaryKey val id: String,
    val name: String
)
