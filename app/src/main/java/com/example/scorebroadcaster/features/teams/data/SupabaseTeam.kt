package com.example.scorebroadcaster.features.teams.data

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Remote representation of a saved team stored in Supabase.
 *
 * Maps to the `teams` table with columns: id, user_id, name, created_at, updated_at.
 * The [id] matches the local [SavedTeam.id] so the same UUID is used across local
 * Room storage and the remote `teams` table.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SupabaseTeam(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("created_at") val createdAt: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("updated_at") val updatedAt: String? = null
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
