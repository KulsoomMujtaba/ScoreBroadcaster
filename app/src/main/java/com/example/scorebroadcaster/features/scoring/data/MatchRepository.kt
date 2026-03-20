package com.example.scorebroadcaster.features.scoring.data
import android.util.Log
import com.example.scorebroadcaster.features.match.data.Match
import com.example.scorebroadcaster.features.match.data.MatchDao
import com.example.scorebroadcaster.features.match.data.toDomain
import com.example.scorebroadcaster.features.match.data.toEntity
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
 */
class MatchRepository(
    private val dao: MatchDao,
    private val ballEventDao: BallEventDao,
    private val scope: CoroutineScope
) {

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
        scope.launch { dao.insert(match.toEntity()) }
    }

    fun updateMatch(match: Match) {
        scope.launch { dao.update(match.toEntity()) }
        if (_activeMatch.value?.id == match.id) {
            _activeMatch.value = match
        }
    }

    fun setActiveMatch(match: Match) {
        _activeMatch.value = match
    }

    fun clearActiveMatch() {
        _activeMatch.value = null
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
    }
}
