package com.devhub.scored.features.teams.data
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.devhub.scored.features.players.data.Player

/**
 * Room entity for a reusable saved team template stored locally.
 *
 * Mirrors [SavedTeam]. The [players] list is stored as a JSON string via
 * [PlayerListTypeConverter]; this avoids a separate join table given that
 * [Player] is a lightweight value snapshot (not a standalone entity).
 *
 * Use [toDomain] / [SavedTeam.toEntity] to convert between the two.
 */
@Entity(tableName = "saved_teams")
data class SavedTeamEntity(
    @PrimaryKey val id: String,
    val name: String,
    val players: List<Player> = emptyList()
)

/** Convert a Room entity to the domain model. */
fun SavedTeamEntity.toDomain(): SavedTeam = SavedTeam(
    id = id,
    name = name,
    players = players
)

/** Convert a domain model to a Room entity. */
fun SavedTeam.toEntity(): SavedTeamEntity = SavedTeamEntity(
    id = id,
    name = name,
    players = players
)
