package com.example.scorebroadcaster.repository

import android.util.Log
import com.example.scorebroadcaster.data.entity.Match
import com.example.scorebroadcaster.data.local.BallEventDao
import com.example.scorebroadcaster.data.local.MatchDao
import com.example.scorebroadcaster.data.local.toDomain
import com.example.scorebroadcaster.data.local.toEntity
import com.example.scorebroadcaster.domain.BallEvent
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
    }
}
