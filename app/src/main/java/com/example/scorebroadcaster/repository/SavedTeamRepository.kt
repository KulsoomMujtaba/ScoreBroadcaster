package com.example.scorebroadcaster.repository

import com.example.scorebroadcaster.data.entity.SavedTeam
import com.example.scorebroadcaster.data.local.SavedTeamDao
import com.example.scorebroadcaster.data.local.toDomain
import com.example.scorebroadcaster.data.local.toEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Room-backed repository for reusable saved team templates.
 *
 * Replaces the previous in-memory singleton. Teams now persist across app
 * restarts via the [SavedTeamDao] / Room database.
 *
 * Architecture note:
 * - [teams] returns a synchronous snapshot backed by an in-memory StateFlow that
 *   is kept in sync with Room via [observeAll].
 * - [teamFlow] exposes a reactive stream for consumers that prefer Flow.
 * - Mutations ([addTeam], [removeTeam], [updateTeam]) are non-suspending;
 *   Room writes are dispatched on the provided [scope] and the StateFlow updates
 *   automatically when Room confirms the change.
 */
class SavedTeamRepository(
    private val dao: SavedTeamDao,
    private val scope: CoroutineScope
) {
    /** Reactive stream of all saved teams, ordered by name. */
    val teamFlow: Flow<List<SavedTeam>> = dao.observeAll()
        .map { entities -> entities.map { it.toDomain() } }

    private val _state = teamFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    /** Synchronous snapshot of the current team list (backed by the Room-derived StateFlow). */
    val teams: List<SavedTeam>
        get() = _state.value

    fun addTeam(team: SavedTeam) {
        scope.launch { dao.insert(team.toEntity()) }
    }

    fun removeTeam(id: String) {
        scope.launch {
            val entity = dao.getById(id) ?: return@launch
            dao.delete(entity)
        }
    }

    fun updateTeam(team: SavedTeam) {
        scope.launch { dao.update(team.toEntity()) }
    }

    fun findById(id: String): SavedTeam? = _state.value.find { it.id == id }
}
