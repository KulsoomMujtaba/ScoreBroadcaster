package com.devhub.scored.features.players.data

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Remote representation of a private player profile stored in Supabase.
 *
 * Maps to the `players` table with columns: id, user_id, name, created_at, updated_at.
 * The [id] matches the local [PlayerProfile.id] so duplicate-checking can be
 * done by comparing ids.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SupabasePlayer(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("created_at") val createdAt: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("updated_at") val updatedAt: String? = null
)

/** Convert a [PlayerProfile] to its remote [SupabasePlayer] representation. */
fun PlayerProfile.toSupabasePlayer(userId: String): SupabasePlayer = SupabasePlayer(
    id = id,
    userId = userId,
    name = displayName
)

/** Convert a remote [SupabasePlayer] back to a local [PlayerProfile] domain model. */
fun SupabasePlayer.toPlayerProfile(): PlayerProfile = PlayerProfile(
    id = id,
    displayName = name,
    playerSourceType = PlayerSourceType.PRIVATE
)
