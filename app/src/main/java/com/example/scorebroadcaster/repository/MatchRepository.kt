package com.example.scorebroadcaster.repository

import android.util.Log
import com.example.scorebroadcaster.data.entity.Match
import com.example.scorebroadcaster.data.local.MatchDao
import com.example.scorebroadcaster.data.local.toDomain
import com.example.scorebroadcaster.data.local.toEntity
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
