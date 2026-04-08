package com.example.scorebroadcaster.features.scoring.data

import android.util.Log
import com.example.scorebroadcaster.core.supabase.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

/**
 * Handles all Supabase `match_events` table operations.
 *
 * The `match_events` table is append-only — rows are never updated or deleted.
 * The full match state is rebuilt by fetching all rows for a match and replaying
 * them through the scoring reducer.
 *
 * Responsibilities:
 * - [insertEvent]        — append a single delivery event to the remote log.
 * - [fetchMatchEvents]   — retrieve the full ordered event log for one match.
 *
 * All functions are `suspend` and must be called from a coroutine context.
 * No calls are made directly from Composables.
 *
 * If the Supabase client is not configured, or if any remote call fails, the
 * function logs the error and returns a safe default; local scoring is never blocked.
 */
object SupabaseEventRepository {

    private const val TAG = "SupabaseEventRepo"
    private const val TABLE = "match_events"

    private val client get() = SupabaseClientProvider.clientOrNull

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Append [event] to the `match_events` table.
     *
     * Uses upsert on the [SupabaseEvent.id] primary key (which is deterministic:
     * `"<matchId>_<eventIndex>"`).  If a row with the same `id` already exists, Supabase
     * performs an UPDATE with the same data — harmless because events are immutable.
     * This makes the insert idempotent and safe to retry after a transient failure.
     *
     * Does nothing if the Supabase client is not configured.
     */
    suspend fun insertEvent(event: SupabaseEvent) {
        val supabase = client ?: return
        runCatching {
            supabase.postgrest[TABLE]
                .upsert(event) { onConflict = "id" }
            Log.d(TAG, "Inserted event index ${event.eventIndex} for match ${event.matchId}")
        }.onFailure { e ->
            Log.e(TAG, "Failed to insert event index ${event.eventIndex}: ${e.message}")
        }
    }

    /**
     * Fetch all remote delivery events for [matchId], ordered by [SupabaseEvent.eventIndex].
     *
     * @return ordered list of [SupabaseEvent], or an empty list if the client is not
     *         configured or the request fails.
     */
    suspend fun fetchMatchEvents(matchId: String): List<SupabaseEvent> {
        val supabase = client ?: return emptyList()
        return runCatching {
            val result = supabase.postgrest[TABLE]
                .select(columns = Columns.ALL) {
                    filter { eq("match_id", matchId) }
                    order("event_index", Order.ASCENDING)
                }
                .decodeList<SupabaseEvent>()
            Log.d(TAG, "Fetched ${result.size} events for match $matchId")
            result
        }.getOrElse { e ->
            Log.e(TAG, "Failed to fetch events for match $matchId: ${e.message}")
            emptyList()
        }
    }

    /**
     * Subscribe to INSERT events on `match_events` for [matchId] using Supabase Realtime.
     *
     * Returns a [RealtimeChannel] (needed for cleanup) and a [Flow] of incoming [SupabaseEvent]s.
     * The caller is responsible for calling [RealtimeChannel.unsubscribe] when done — typically
     * when the viewer screen closes or the ViewModel is cleared.
     *
     * Returns `null` if the Supabase client is not configured or subscription setup fails.
     */
    suspend fun subscribeToMatchEvents(matchId: String): Pair<RealtimeChannel, Flow<SupabaseEvent>>? {
        val supabase = client ?: return null
        return runCatching {
            val channel = supabase.channel("viewer-match-$matchId")
            val insertFlow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = TABLE
                filter = "match_id=eq.$matchId"
            }
            channel.subscribe()
            Log.d(TAG, "Subscribed to match $matchId")
            val eventFlow: Flow<SupabaseEvent> = insertFlow.mapNotNull { action ->
                runCatching { action.decodeRecord<SupabaseEvent>() }.getOrElse { e ->
                    Log.e(TAG, "Failed to decode realtime event for match $matchId: ${e.message}")
                    null
                }
            }
            Pair(channel, eventFlow)
        }.getOrElse { e ->
            Log.e(TAG, "Failed to subscribe to match $matchId: ${e.message}")
            null
        }
    }
}
