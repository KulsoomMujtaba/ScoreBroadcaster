package com.devhub.scored.features.auth.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * App-level user profile linked to a Supabase auth user.
 *
 * The [id] matches the Supabase `auth.users` id so every profile is uniquely
 * owned by one authenticated user.
 */
@Serializable
data class UserProfile(
    val id: String,
    val email: String,
    @SerialName("display_name") val displayName: String? = null
)
