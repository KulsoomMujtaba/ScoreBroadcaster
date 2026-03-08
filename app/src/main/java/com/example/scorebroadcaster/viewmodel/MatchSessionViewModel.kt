package com.example.scorebroadcaster.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.scorebroadcaster.data.entity.Match
import com.example.scorebroadcaster.data.entity.MatchStatus
import com.example.scorebroadcaster.data.entity.PlayerProfile
import com.example.scorebroadcaster.data.entity.SavedTeam
import com.example.scorebroadcaster.data.local.ScoredDatabase
import com.example.scorebroadcaster.repository.MatchRepository
import com.example.scorebroadcaster.repository.SavedPlayerRepository
import com.example.scorebroadcaster.repository.SavedTeamRepository
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Manages the higher-level match lifecycle: creation, player setup, active session, and match list.
 * Also manages saved (reusable) teams and saved (reusable) player profiles.
 * Works alongside [MatchViewModel], which handles ball-by-ball scoring events.
 */
class MatchSessionViewModel(application: Application) : AndroidViewModel(application) {

    private val db = ScoredDatabase.getInstance(application)

    private val savedPlayerRepository = SavedPlayerRepository(
        dao = db.playerProfileDao(),
        scope = viewModelScope
    )

    private val savedTeamRepository = SavedTeamRepository(
        dao = db.savedTeamDao(),
        scope = viewModelScope
    )

    private val _matches = MutableStateFlow<List<Match>>(MatchRepository.matches)
    val matches: StateFlow<List<Match>> = _matches.asStateFlow()

    private val _activeMatch = MutableStateFlow<Match?>(MatchRepository.activeMatch)
    val activeMatch: StateFlow<Match?> = _activeMatch.asStateFlow()

    /** Draft match being assembled across the Create → Players → Summary flow. */
    private val _pendingMatch = MutableStateFlow<Match?>(null)
    val pendingMatch: StateFlow<Match?> = _pendingMatch.asStateFlow()

    // ---------------------------------------------------------------------------
    // Saved teams — reactive, driven by Room Flow
    // ---------------------------------------------------------------------------

    /** Reactive list of saved teams, driven directly by the Room Flow. */
    val savedTeams: StateFlow<List<SavedTeam>> = savedTeamRepository.teamFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    /** Persist a new saved team. The StateFlow updates automatically via Room. */
    fun addSavedTeam(team: SavedTeam) {
        savedTeamRepository.addTeam(team)
    }

    /** Remove a saved team by id. The StateFlow updates automatically via Room. */
    fun removeSavedTeam(id: String) {
        savedTeamRepository.removeTeam(id)
    }

    // ---------------------------------------------------------------------------
    // Saved player profiles
    // ---------------------------------------------------------------------------

    /** Reactive list of saved player profiles, driven directly by the Room Flow. */
    val savedPlayers: StateFlow<List<PlayerProfile>> = savedPlayerRepository.playerFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    /** Persist a new private player profile. The StateFlow updates automatically via Room. */
    fun addSavedPlayer(player: PlayerProfile) {
        savedPlayerRepository.addPlayer(player)
    }

    /** Remove a saved player profile by id. The StateFlow updates automatically via Room. */
    fun removeSavedPlayer(id: String) {
        savedPlayerRepository.removePlayer(id)
    }

    // ---------------------------------------------------------------------------
    // Match creation / session management
    // ---------------------------------------------------------------------------

    /** Save an incomplete draft so subsequent setup screens can read and update it. */
    fun setPendingMatch(match: Match) {
        _pendingMatch.value = match
    }

    /**
     * Finalise the pending match: persist to the repository, mark it as active, and clear the draft.
     */
    fun confirmMatch(match: Match) {
        val confirmed = match.copy(status = MatchStatus.IN_PROGRESS)
        MatchRepository.addMatch(confirmed)
        MatchRepository.setActiveMatch(confirmed)
        _matches.value = MatchRepository.matches
        _activeMatch.value = confirmed
        _pendingMatch.value = null
    }

    /** Switch the active session to a previously created match. */
    fun setActiveMatch(match: Match) {
        MatchRepository.setActiveMatch(match)
        _activeMatch.value = match
    }

    /** Refresh match state from the in-memory repository (e.g. after returning to My Matches).
     *  savedTeams and savedPlayers are driven by Room Flows and update automatically. */
    fun refresh() {
        _matches.value = MatchRepository.matches
        _activeMatch.value = MatchRepository.activeMatch
    }
}
