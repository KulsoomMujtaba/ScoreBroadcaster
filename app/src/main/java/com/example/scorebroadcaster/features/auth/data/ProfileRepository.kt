package com.example.scorebroadcaster.features.auth.data

import com.example.scorebroadcaster.core.supabase.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns

/**
 * Handles all Supabase `profiles` table operations.
 *
 * Responsibilities:
 * - [getCurrentProfile] — fetch the signed-in user's profile row.
 * - [upsertProfile] — insert the profile if it does not yet exist, or update it.
 *
 * All calls are `suspend` functions and must be invoked from a coroutine context.
 */
object ProfileRepository {

    private val client get() = SupabaseClientProvider.clientOrNull

    private const val CONFLICT_COLUMN = "id"

    /**
     * Returns the current user's [UserProfile], or `null` if the client is not
     * configured, no user is signed in, or no profile row exists yet.
     */
    suspend fun getCurrentProfile(): UserProfile? {
        val supabase = client ?: return null
        val userId = supabase.auth.currentUserOrNull()?.id ?: return null
        return runCatching {
            supabase.postgrest["profiles"]
                .select(columns = Columns.ALL) {
                    filter { eq("id", userId) }
                }
                .decodeSingleOrNull<UserProfile>()
        }.getOrNull()
    }

    /**
     * Creates the profile row if it does not yet exist, or updates it if it does.
     *
     * Safe to call after every successful sign-in, sign-up, and session restore —
     * the upsert operation is idempotent and will never create duplicates.
     *
     * @return the resulting [UserProfile], or `null` on error / missing config.
     */
    suspend fun upsertProfile(): UserProfile? {
        val supabase = client ?: return null
        val authUser = supabase.auth.currentUserOrNull() ?: return null
        val profile = UserProfile(
            id = authUser.id,
            email = authUser.email ?: return null
        )
        return runCatching {
            supabase.postgrest["profiles"]
                .upsert(profile) { onConflict = CONFLICT_COLUMN }
                .decodeSingleOrNull<UserProfile>()
                ?: profile
        }.getOrNull()
    }
}
