package com.example.scorebroadcaster.features.match.viewmodel
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.scorebroadcaster.features.match.data.Match
import com.example.scorebroadcaster.features.match.data.MatchStatus
import com.example.scorebroadcaster.features.players.data.PlayerProfile
import com.example.scorebroadcaster.features.teams.data.SavedTeam
import com.example.scorebroadcaster.features.match.data.ScoredDatabase
import com.example.scorebroadcaster.features.scoring.data.MatchRepository
import com.example.scorebroadcaster.features.players.data.SavedPlayerRepository
import com.example.scorebroadcaster.features.teams.data.SavedTeamRepository
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
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

    private val matchRepository = MatchRepository(
        dao = db.matchDao(),
        ballEventDao = db.ballEventDao(),
        scope = viewModelScope
    ).also { MatchRepository.setInstance(it) }

    /** Reactive list of all matches, newest first, driven by Room. */
    val matches: StateFlow<List<Match>> = matchRepository.matchFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    private val _activeMatch = MutableStateFlow<Match?>(matchRepository.activeMatch)
    val activeMatch: StateFlow<Match?> = _activeMatch.asStateFlow()

    /**
     * The most recent non-completed match from local storage that can be resumed.
     *
     * Derived reactively from the Room-backed [matchFlow] so it survives app restarts:
     * as soon as the database emits a match with [Match.isResumable] == true, this
     * StateFlow updates — no explicit call is required on app open.
     *
     * Useful for driving the "Resume In-Progress Match" card on the Home screen when
     * [activeMatch] is null (e.g. immediately after an app restart).
     */
    val resumableMatch: StateFlow<Match?> = matchRepository.matchFlow
        .map { list -> list.firstOrNull { it.isResumable } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

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

    /** Id of the currently authenticated user, set via [syncPlayersForUser]. */
    private var currentUserId: String? = null

    /** Persist a new private player profile locally and mirror it to Supabase when signed in. */
    fun addSavedPlayer(player: PlayerProfile) {
        savedPlayerRepository.addPlayer(player, currentUserId)
    }

    /** Remove a saved player profile by id. The StateFlow updates automatically via Room. */
    fun removeSavedPlayer(id: String) {
        savedPlayerRepository.removePlayer(id)
    }

    /**
     * Trigger the one-way local → Supabase sync for the signed-in [userId].
     *
     * Safe to call multiple times (e.g. every time the profile loads); the sync
     * strategy is idempotent.  Also stores [userId] so that subsequent calls to
     * [addSavedPlayer] automatically mirror new players to Supabase.
     */
    fun syncPlayersForUser(userId: String) {
        currentUserId = userId
        savedPlayerRepository.syncWithRemote(userId)
    }

    // ---------------------------------------------------------------------------
    // Match creation / session management
    // ---------------------------------------------------------------------------

    /** Save an incomplete draft so subsequent setup screens can read and update it. */
    fun setPendingMatch(match: Match) {
        _pendingMatch.value = match
    }

    /**
     * Finalise the pending match: persist to Room, mark it as active, and clear the draft.
     * The [matches] StateFlow updates automatically via the Room-backed flow.
     */
    fun confirmMatch(match: Match) {
        val confirmed = match.copy(status = MatchStatus.IN_PROGRESS)
        matchRepository.addMatch(confirmed)
        matchRepository.setActiveMatch(confirmed)
        _activeMatch.value = confirmed
        _pendingMatch.value = null
    }

    /** Switch the active session to a previously created match. */
    fun setActiveMatch(match: Match) {
        matchRepository.setActiveMatch(match)
        _activeMatch.value = match
    }

    /**
     * Clear the active match, ending the current scoring session.
     *
     * Called by reset flows to ensure the Home screen no longer shows a stale active-match
     * banner after scoring state has been cleared.  The match itself remains in local storage
     * and may still appear in [resumableMatch] if its status is still [MatchStatus.IN_PROGRESS]
     * or [MatchStatus.INNINGS_BREAK].
     */
    fun clearActiveMatch() {
        matchRepository.clearActiveMatch()
        _activeMatch.value = null
    }

    /** Refresh active-match state from the repository (e.g. after returning to My Matches).
     *  [matches] and saved teams/players are driven by Room Flows and update automatically. */
    fun refresh() {
        _activeMatch.value = matchRepository.activeMatch
    }
}
