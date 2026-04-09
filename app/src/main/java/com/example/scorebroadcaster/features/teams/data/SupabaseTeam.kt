package com.example.scorebroadcaster.features.teams.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Remote representation of a saved team stored in Supabase.
 *
 * Maps to the `teams` table with columns: id, user_id, name, created_at, updated_at.
 * The [id] matches the local [SavedTeam.id] so the same UUID is used across local
 * Room storage and the remote `teams` table.
 */
@Serializable
data class SupabaseTeam(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = ""
)

/** Convert a [SavedTeam] to its remote [SupabaseTeam] representation. */
fun SavedTeam.toSupabaseTeam(userId: String): SupabaseTeam = SupabaseTeam(
    id = id,
    userId = userId,
    name = name
)

/** Convert a remote [SupabaseTeam] back to a local [SavedTeam] domain model (without players). */
fun SupabaseTeam.toSavedTeam(): SavedTeam = SavedTeam(
    id = id,
    name = name
)
