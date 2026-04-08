package com.example.scorebroadcaster.features.scoring.data
import android.util.Log
import com.example.scorebroadcaster.features.match.data.Match
import com.example.scorebroadcaster.features.match.data.MatchDao
import com.example.scorebroadcaster.features.match.data.MatchVisibility
import com.example.scorebroadcaster.features.match.data.SupabaseMatchRepository
import com.example.scorebroadcaster.features.match.data.toDomain
import com.example.scorebroadcaster.features.match.data.toEntity
import com.example.scorebroadcaster.features.match.data.toSupabaseMatch
import com.example.scorebroadcaster.features.scoring.domain.BallEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Room-backed repository for matches.
 *
 * Matches are persisted to the local Room database and survive app restarts.
 * [matchFlow] provides a reactive stream ordered newest-first; [matches] offers
 * synchronous access to the latest snapshot via a shared StateFlow.
 *
 * The active match (the one currently being scored) is kept in memory only;
 * it is not persisted by this class.
 *
 * Architecture note – one-scorer / many-viewers:
 * Every match has exactly one scorer (the device that calls [addMatch]).  Viewer
 * access will be mediated by a remote backend and gated on [MatchVisibility]; the
 * local repository never needs to enforce viewer permissions.
 *
 * **Companion object bridge**: [MatchViewModel] calls [MatchRepository.updateMatch]
 * as a static call on the companion object.  The companion delegates to the active
 * instance set by [MatchSessionViewModel] via [setInstance].  This lets
 * [MatchViewModel] remain unchanged while the repository gains Room persistence.
 *
 * **Remote sync**: When a [currentUserId] is set via [setCurrentUser], [addMatch]
 * and [updateMatch] also mirror changes to Supabase asynchronously so the UI is
 * never blocked. Local scoring continues uninterrupted if any remote call fails.
 */
class MatchRepository(
    private val dao: MatchDao,
    private val ballEventDao: BallEventDao,
    private val scope: CoroutineScope
) {

    /** Id of the currently authenticated user; set via [setCurrentUser]. */
    private var currentUserId: String? = null

    /**
     * Register the authenticated user so that subsequent [addMatch] and [updateMatch]
     * calls also mirror changes to Supabase.
     */
    fun setCurrentUser(userId: String) {
        currentUserId = userId
    }

    /** Reactive list of all matches, ordered by creation date (newest first). */
    val matchFlow: Flow<List<Match>> = dao.observeAll()
        .map { entities -> entities.map { it.toDomain() } }

    private val _state = matchFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val matches: List<Match>
        get() = _state.value

    private val _activeMatch = MutableStateFlow<Match?>(null)

    val activeMatch: Match?
        get() = _activeMatch.value

    fun addMatch(match: Match) {
        scope.launch {
            dao.insert(match.toEntity())
            val userId = currentUserId
            if (userId != null) {
                Log.d("MatchRepository", "Match inserted: ${match.displayTitle}")
                SupabaseMatchRepository.upsertMatch(match.toSupabaseMatch(userId))
            }
        }
    }

    /**
     * Delete the match with [matchId] from the local Room database and asynchronously remove
     * it from Supabase.
     *
     * Supabase will cascade-delete all associated `match_events` rows automatically.
     * The local Room deletion is immediate so the UI updates optimistically; the remote
     * delete is best-effort and does not block scoring or navigation.
     */
    fun deleteMatch(matchId: String) {
        Log.d("MatchRepository", "Deleting match $matchId")
        // Clear active match synchronously for immediate UI response (optimistic update).
        if (_activeMatch.value?.localId == matchId) {
            _activeMatch.value = null
        }
        scope.launch {
            dao.deleteById(matchId)
            ballEventDao.deleteForMatch(matchId)
            val userId = currentUserId
            if (userId != null) {
                SupabaseMatchRepository.deleteMatch(matchId)
            }
        }
    }

    fun updateMatch(match: Match) {
        scope.launch {
            dao.update(match.toEntity())
            val userId = currentUserId
            if (userId != null) {
                Log.d("MatchRepository", "Match updated: ${match.displayTitle}")
                SupabaseMatchRepository.upsertMatch(match.toSupabaseMatch(userId))
            }
        }
        if (_activeMatch.value?.id == match.id) {
            _activeMatch.value = match
        }
    }

    /**
     * Publish a match: generate a unique share code, update the local match, and
     * mirror the change to Supabase.
     *
     * On success the match is updated locally with:
     * - [MatchVisibility.PUBLISHED] visibility
     * - a generated [Match.shareCode]
     * - the current time as [Match.publishedAt]
     *
     * @return the share code on success, or `null` if the remote call fails or the
     *         Supabase client is not configured.
     */
    suspend fun publishMatch(matchId: String): String? {
        Log.d("MatchRepository", "Publishing match $matchId")
        val match = dao.getById(matchId)?.toDomain() ?: return null
        if (match.shareCode != null && match.visibility == MatchVisibility.PUBLISHED) {
            Log.d("MatchRepository", "Match already published with code ${match.shareCode}")
            return match.shareCode
        }
        val shareCode = SupabaseMatchRepository.publishMatch(matchId) ?: return null
        val published = match.copy(
            visibility = MatchVisibility.PUBLISHED,
            shareCode = shareCode,
            publishedAt = System.currentTimeMillis()
        )
        dao.update(published.toEntity())
        if (_activeMatch.value?.id == matchId) {
            _activeMatch.value = published
        }
        return shareCode
    }

    fun setActiveMatch(match: Match) {
        _activeMatch.value = match
    }

    fun clearActiveMatch() {
        _activeMatch.value = null
    }

    /**
     * Run the bidirectional sync strategy for [userId]:
     *
     * 1. Stores [userId] for use in subsequent [addMatch] / [updateMatch] calls.
     * 2. Reads the current local snapshot.
     * 3. Delegates to [SupabaseMatchRepository.syncMatches] which determines the correct
     *    sync case (A, B, or C) and returns the list of [Match] objects that should
     *    be written into local storage.
     * 4. Inserts / replaces each returned match into the local Room database.
     *
     * All work is done off the main thread; callers do not need to suspend.
     */
    fun syncWithRemote(userId: String) {
        currentUserId = userId
        scope.launch {
            val local = dao.getAll().map { it.toDomain() }
            val toHydrate = SupabaseMatchRepository.syncMatches(local, userId)
            toHydrate.forEach { match -> dao.insert(match.toEntity()) }
        }
    }

    // ---------------------------------------------------------------------------
    // Remote event persistence (Supabase)
    // ---------------------------------------------------------------------------

    /**
     * Asynchronously insert a single [BallEvent] into the remote `match_events` table.
     *
     * Called after every ball is appended locally so the remote log stays up to date.
     * If the Supabase client is unavailable or the insert fails, the error is logged
     * and local scoring continues uninterrupted.
     *
     * @param matchId        The local match UUID (shared with `matches.id` in Supabase).
     * @param inningsNumber  1 for first innings, 2 for second innings.
     * @param sequenceNumber 0-based position of this event within the innings.
     * @param globalIndex    Globally ordered index within the match.
     * @param event          The stamped [BallEvent] to persist.
     */
    fun insertRemoteEvent(
        matchId: String,
        inningsNumber: Int,
        sequenceNumber: Int,
        globalIndex: Int,
        event: BallEvent
    ) {
        val userId = currentUserId ?: return
        scope.launch {
            val supabaseEvent = event.toSupabaseEvent(matchId, userId, inningsNumber, sequenceNumber, globalIndex)
            SupabaseEventRepository.insertEvent(supabaseEvent)
        }
    }

    /**
     * Fetch all remote events for [matchId] from Supabase and save them to the local Room
     * database, replacing any previously stored events for each innings.
     *
     * This is the "load from remote" step used by [initFromMatch] to ensure the most
     * up-to-date event log is available before rebuilding match state, including when
     * the match is opened on a new device.
     *
     * If the remote fetch returns no events (e.g. network unavailable or fresh match),
     * the local Room data is left unchanged.
     */
    suspend fun syncMatchEvents(matchId: String) {
        Log.d("MatchRepository", "Syncing match events for $matchId")
        val remoteEvents = SupabaseEventRepository.fetchMatchEvents(matchId)
        Log.d("MatchRepository", "Fetched ${remoteEvents.size} events for match $matchId")
        if (remoteEvents.isEmpty()) return

        val grouped = remoteEvents.groupBy { it.payload.inningsNumber }
        Log.d("MatchRepository", "Rebuilding match state from ${remoteEvents.size} remote events")
        for ((inningsNumber, events) in grouped) {
            val sorted = events.sortedBy { it.eventIndex }
            val ballEvents = sorted.map { it.toBallEvent() }
            saveBallEvents(matchId, inningsNumber, ballEvents)
        }
    }

    // ---------------------------------------------------------------------------
    // BallEvent persistence
    // ---------------------------------------------------------------------------

    /**
     * Persist all [events] for a given innings.
     *
     * Uses a replace-all strategy: existing rows for [matchLocalId] + [inningsNumber]
     * are deleted and the full list is written in a single transaction.
     */
    suspend fun saveBallEvents(
        matchLocalId: String,
        inningsNumber: Int,
        events: List<BallEvent>
    ) {
        ballEventDao.deleteForInnings(matchLocalId, inningsNumber)
        val entities = events.mapIndexed { index, event ->
            event.toEntity(matchLocalId, inningsNumber, index)
        }
        ballEventDao.insertAll(entities)
    }

    /**
     * Load the persisted [BallEvent] list for a single innings.
     *
     * Returns an empty list if no events have been saved for the given innings.
     */
    suspend fun loadBallEvents(
        matchLocalId: String,
        inningsNumber: Int
    ): List<BallEvent> =
        ballEventDao.getEventsForInnings(matchLocalId, inningsNumber)
            .map { it.toDomain() }

    /**
     * Delete all persisted [BallEvent]s for a match (both innings).
     *
     * Called by reset flows to fully clear the scoring history from the database so
     * that a subsequent [loadAllBallEvents] returns empty lists and does not restore
     * stale state.
     */
    suspend fun deleteAllBallEvents(matchId: String) {
        ballEventDao.deleteForMatch(matchId)
    }

    /**
     * Returns `true` if the match identified by [matchLocalId] has at least one persisted
     * [BallEvent] (i.e. scoring has started), `false` if the event log is empty.
     *
     * Used by the resume flow to decide whether the innings-setup popup should be shown.
     */
    suspend fun hasMatchStarted(matchLocalId: String): Boolean =
        ballEventDao.hasEvents(matchLocalId)

    /**
     * Load all persisted [BallEvent]s for a match, split by innings.
     *
     * @return A [Pair] where [Pair.first] is the first-innings event list and
     *         [Pair.second] is the second-innings event list. Either may be empty.
     */
    suspend fun loadAllBallEvents(
        matchLocalId: String
    ): Pair<List<BallEvent>, List<BallEvent>> {
        val all = ballEventDao.getAllEventsForMatch(matchLocalId)
        val (firstEntities, secondEntities) = all.partition { it.inningsNumber == 1 }
        val first = firstEntities.map { it.toDomain() }
        val second = secondEntities.map { it.toDomain() }
        return Pair(first, second)
    }

    companion object {

        @Volatile
        private var _instance: MatchRepository? = null

        /**
         * Register the active Room-backed repository instance.
         * Called once by [MatchSessionViewModel] after the instance is created.
         */
        @Synchronized
        internal fun setInstance(instance: MatchRepository) {
            _instance = instance
        }

        /**
         * Delete the match with [matchId] from local Room and Supabase.
         *
         * Delegates to the active instance. No-ops if the repository is not yet initialised.
         */
        fun deleteMatch(matchId: String) {
            val repo = _instance ?: return
            repo.deleteMatch(matchId)
        }

        /**
         * Delegates [Match] updates to the active Room-backed instance.
         *
         * This static entry point exists solely so that [MatchViewModel] can
         * call `MatchRepository.updateMatch(match)` without needing a direct
         * reference to the repository instance.
         */
        fun updateMatch(match: Match) {
            val repo = _instance
            if (repo == null) {
                Log.w("MatchRepository", "updateMatch called before repository was initialised; update dropped")
                return
            }
            repo.updateMatch(match)
        }

        /**
         * Persist all [events] for the given innings to Room.
         *
         * Delegates to the active instance.  Logs a warning and no-ops if called before the
         * repository is initialised (should not happen during normal scoring).
         */
        suspend fun saveBallEvents(
            matchId: String,
            inningsNumber: Int,
            events: List<BallEvent>
        ) {
            val repo = _instance
            if (repo == null) {
                Log.w("MatchRepository", "saveBallEvents called before repository was initialised; save dropped")
                return
            }
            repo.saveBallEvents(matchId, inningsNumber, events)
        }

        /**
         * Load all persisted [BallEvent]s for a match, split by innings.
         *
         * Delegates to the active instance.  Returns empty lists if called before the
         * repository is initialised or if no events have been saved yet.
         */
        suspend fun loadAllBallEvents(
            matchId: String
        ): Pair<List<BallEvent>, List<BallEvent>> {
            val repo = _instance ?: return Pair(emptyList(), emptyList())
            return repo.loadAllBallEvents(matchId)
        }

        /**
         * Delete all persisted [BallEvent]s for a match (both innings).
         *
         * Delegates to the active instance.  No-ops if called before the repository is
         * initialised.  Used by reset flows to clear stale scoring history from the DB.
         */
        suspend fun deleteAllBallEvents(matchId: String) {
            val repo = _instance ?: return
            repo.deleteAllBallEvents(matchId)
        }

        /**
         * Returns `true` if the match identified by [matchId] has at least one persisted
         * [BallEvent] (i.e. scoring has started), `false` otherwise.
         *
         * Delegates to the active instance.  Returns `false` if called before the repository
         * is initialised (treated as no events).
         */
        suspend fun hasMatchStarted(matchId: String): Boolean {
            val repo = _instance ?: return false
            return repo.hasMatchStarted(matchId)
        }

        /**
         * Asynchronously insert a single [BallEvent] into the remote `match_events` table.
         *
         * Delegates to the active instance.  No-ops if the repository is not yet initialised
         * or if no user is signed in.
         */
        fun insertRemoteEvent(
            matchId: String,
            inningsNumber: Int,
            sequenceNumber: Int,
            globalIndex: Int,
            event: BallEvent
        ) {
            val repo = _instance ?: return
            repo.insertRemoteEvent(matchId, inningsNumber, sequenceNumber, globalIndex, event)
        }

        /**
         * Fetch all remote events for [matchId] from Supabase and update the local Room DB.
         *
         * Delegates to the active instance.  No-ops if the repository is not yet initialised.
         */
        suspend fun syncMatchEvents(matchId: String) {
            val repo = _instance ?: return
            repo.syncMatchEvents(matchId)
        }

        /**
         * Publish a match and return the generated share code.
         *
         * Delegates to the active instance.  Returns `null` if the repository is not yet
         * initialised or the publish operation fails.
         */
        suspend fun publishMatch(matchId: String): String? {
            val repo = _instance ?: return null
            return repo.publishMatch(matchId)
        }
    }
}
