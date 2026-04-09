package com.example.scorebroadcaster.features.match.data

import android.util.Log
import com.example.scorebroadcaster.core.supabase.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns

object SupabaseMatchRepository {

    private const val TAG = "SupabaseMatchRepo"
    private const val TABLE = "matches"
    private const val CONFLICT_COLUMN = "id"
    private const val SHARE_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private const val SHARE_CODE_LENGTH = 7
    private const val SHARE_CODE_MAX_RETRIES = 5

    private val client get() = SupabaseClientProvider.clientOrNull

    suspend fun fetchRemoteMatches(userId: String): List<SupabaseMatch> {
        val supabase = client ?: run {
            Log.e(TAG, "fetchRemoteMatches: Supabase client is NULL — check SUPABASE_URL and SUPABASE_ANON_KEY in local.properties")
            return emptyList()
        }
        Log.d(TAG, "fetchRemoteMatches: fetching for userId=$userId")
        return runCatching {
            val result = supabase.postgrest[TABLE]
                .select(columns = Columns.ALL) {
                    filter { eq("user_id", userId) }
                }
                .decodeList<SupabaseMatch>()
            Log.d(TAG, "fetchRemoteMatches: got ${result.size} rows")
            result
        }.getOrElse { e ->
            Log.e(TAG, "fetchRemoteMatches: FAILED — ${e::class.simpleName}: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun upsertMatch(match: SupabaseMatch) {
        val supabase = client ?: run {
            Log.e(TAG, "upsertMatch: Supabase client is NULL — match '${match.matchName}' (${match.id}) will NOT be saved to Supabase")
            return
        }
        Log.d(TAG, "upsertMatch: attempting — id=${match.id} name='${match.matchName}' userId=${match.userId} status=${match.status} isPublished=${match.isPublished}")
        Log.d(TAG, "upsertMatch: payload — teamAId=${match.teamAId} teamBId=${match.teamBId} format=${match.format} overs=${match.totalOvers} tossDecision=${match.tossDecision}")
        runCatching {
            supabase.postgrest[TABLE]
                .upsert(match) { onConflict = CONFLICT_COLUMN }
            Log.d(TAG, "upsertMatch: SUCCESS — id=${match.id} name='${match.matchName}'")
        }.onFailure { e ->
            // Log full exception type + message + stack trace so we can see exactly
            // what Supabase rejected (e.g. unknown column, RLS violation, type mismatch).
            Log.e(TAG, "upsertMatch: FAILED — ${e::class.simpleName}: ${e.message}", e)
        }
    }

    suspend fun publishMatch(matchId: String): String? {
        val supabase = client ?: return null
        Log.d(TAG, "publishMatch: matchId=$matchId")
        repeat(SHARE_CODE_MAX_RETRIES) { attempt ->
            val code = generateShareCode()
            Log.d(TAG, "publishMatch: trying share code $code (attempt ${attempt + 1})")
            val collision = runCatching {
                supabase.postgrest[TABLE]
                    .select(columns = io.github.jan.supabase.postgrest.query.Columns.list("id")) {
                        filter { eq("share_code", code) }
                    }
                    .decodeList<ShareCodeCheck>()
                    .isNotEmpty()
            }.getOrElse { e ->
                Log.e(TAG, "publishMatch: collision check FAILED — ${e::class.simpleName}: ${e.message}", e)
                false
            }
            if (!collision) {
                val updated = runCatching {
                    supabase.postgrest[TABLE]
                        .update(PublishPatch(isPublished = true, shareCode = code)) {
                            filter { eq("id", matchId) }
                        }
                    Log.d(TAG, "publishMatch: SUCCESS — matchId=$matchId shareCode=$code")
                    code
                }.getOrElse { e ->
                    Log.e(TAG, "publishMatch: update FAILED — ${e::class.simpleName}: ${e.message}", e)
                    null
                }
                if (updated != null) return updated
            }
        }
        Log.e(TAG, "publishMatch: exhausted $SHARE_CODE_MAX_RETRIES attempts for matchId=$matchId")
        return null
    }

    suspend fun deleteMatch(matchId: String) {
        val supabase = client ?: return
        Log.d(TAG, "deleteMatch: matchId=$matchId")
        runCatching {
            supabase.postgrest[TABLE].delete { filter { eq("id", matchId) } }
            Log.d(TAG, "deleteMatch: SUCCESS — matchId=$matchId")
        }.onFailure { e ->
            Log.e(TAG, "deleteMatch: FAILED — ${e::class.simpleName}: ${e.message}", e)
        }
    }

    suspend fun getMatchByShareCode(shareCode: String): SupabaseMatch? {
        val supabase = client ?: return null
        Log.d(TAG, "getMatchByShareCode: shareCode=$shareCode")
        return runCatching {
            val results = supabase.postgrest[TABLE]
                .select(columns = io.github.jan.supabase.postgrest.query.Columns.ALL) {
                    filter {
                        eq("share_code", shareCode)
                        eq("is_published", true)
                    }
                }
                .decodeList<SupabaseMatch>()
            results.firstOrNull().also { match ->
                if (match != null) Log.d(TAG, "getMatchByShareCode: found match ${match.id}")
                else Log.d(TAG, "getMatchByShareCode: no published match for code=$shareCode")
            }
        }.getOrElse { e ->
            Log.e(TAG, "getMatchByShareCode: FAILED — ${e::class.simpleName}: ${e.message}", e)
            null
        }
    }

    private val secureRandom = java.security.SecureRandom()

    private fun generateShareCode(): String =
        (1..SHARE_CODE_LENGTH)
            .map { SHARE_CODE_CHARS[secureRandom.nextInt(SHARE_CODE_CHARS.length)] }
            .joinToString("")

    @kotlinx.serialization.Serializable
    private data class ShareCodeCheck(val id: String)

    @kotlinx.serialization.Serializable
    private data class PublishPatch(
        @kotlinx.serialization.SerialName("is_published") val isPublished: Boolean,
        @kotlinx.serialization.SerialName("share_code") val shareCode: String
    )

    suspend fun syncMatches(
        localMatches: List<Match>,
        userId: String
    ): List<Match> {
        Log.d(TAG, "syncMatches: starting — localCount=${localMatches.size} userId=$userId")

        if (client == null) {
            Log.e(TAG, "syncMatches: Supabase client is NULL — skipping sync entirely. No matches will be pushed or pulled.")
            return emptyList()
        }

        val remoteMatches = fetchRemoteMatches(userId)
        Log.d(TAG, "syncMatches: remoteCount=${remoteMatches.size}")

        return when {
            localMatches.isEmpty() && remoteMatches.isNotEmpty() -> {
                Log.d(TAG, "syncMatches: CASE A — hydrating local from ${remoteMatches.size} remote matches")
                remoteMatches.map { it.toMatch() }
            }

            localMatches.isNotEmpty() && remoteMatches.isEmpty() -> {
                Log.d(TAG, "syncMatches: CASE B — pushing ${localMatches.size} local matches to remote")
                localMatches.forEach { match ->
                    Log.d(TAG, "syncMatches: CASE B pushing matchId=${match.localId} name='${match.displayTitle}'")
                    upsertMatch(match.toSupabaseMatch(userId))
                }
                emptyList()
            }

            localMatches.isNotEmpty() && remoteMatches.isNotEmpty() -> {
                val remoteIds = remoteMatches.map { it.id }.toHashSet()
                val localOnly = localMatches.filter { it.localId !in remoteIds }
                Log.d(TAG, "syncMatches: CASE C — localOnly=${localOnly.size} (present locally but missing from remote)")
                localOnly.forEach { match ->
                    Log.d(TAG, "syncMatches: CASE C pushing missing matchId=${match.localId} name='${match.displayTitle}'")
                    upsertMatch(match.toSupabaseMatch(userId))
                }
                Log.d(TAG, "syncMatches: CASE C returning ${remoteMatches.size} remote matches as authoritative")
                remoteMatches.map { it.toMatch() }
            }

            else -> {
                Log.d(TAG, "syncMatches: both empty — nothing to do")
                emptyList()
            }
        }.also {
            Log.d(TAG, "syncMatches: complete")
        }
    }
}