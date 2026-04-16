package com.devhub.scored.features.auth.data

import android.util.Log
import com.devhub.scored.core.supabase.SupabaseClientProvider
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

    private const val TAG = "ProfileRepository"
    private val client get() = SupabaseClientProvider.clientOrNull

    private const val CONFLICT_COLUMN = "id"

    /**
     * Returns the current user's [UserProfile], or `null` if the client is not
     * configured, no user is signed in, or no profile row exists yet.
     */
    suspend fun getCurrentProfile(): UserProfile? {
        val supabase = client ?: run {
            Log.w(TAG, "getCurrentProfile: Supabase client not configured")
            return null
        }
        val userId = supabase.auth.currentUserOrNull()?.id
        if (userId == null) {
            Log.w(TAG, "getCurrentProfile: no auth user available")
            return null
        }

        return try {
            Log.d(TAG, "getCurrentProfile: fetching profile for userId=$userId")
            val result = supabase.postgrest["profiles"]
                .select(columns = Columns.ALL) {
                    filter { eq("id", userId) }
                }
                .decodeSingleOrNull<UserProfile>()
            Log.d(TAG, "getCurrentProfile: result=$result")
            result
        } catch (t: Throwable) {
            Log.e(TAG, "getCurrentProfile failed", t)
            null
        }
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
        val supabase = client ?: run {
            Log.w(TAG, "upsertProfile: Supabase client not configured")
            return null
        }
        val authUser = supabase.auth.currentUserOrNull()
        if (authUser == null) {
            Log.w(TAG, "upsertProfile: no auth user available")
            return null
        }
        val email = authUser.email
        if (email.isNullOrBlank()) {
            Log.w(TAG, "upsertProfile: auth user has no email (id=${authUser.id})")
            return null
        }

        val profile = UserProfile(id = authUser.id, email = email)
        return try {
            Log.d(TAG, "upsertProfile: upserting profile for id=${profile.id}, email=${profile.email}")
            val maybe = supabase.postgrest["profiles"]
                .upsert(profile) { onConflict = CONFLICT_COLUMN }
                .decodeSingleOrNull<UserProfile>()
            if (maybe == null) {
                Log.d(TAG, "upsertProfile: upsert returned null — returning local profile")
                profile
            } else {
                Log.d(TAG, "upsertProfile: result from server=$maybe")
                maybe
            }
        } catch (t: Throwable) {
            Log.e(TAG, "upsertProfile failed", t)
            null
        }
    }
}
