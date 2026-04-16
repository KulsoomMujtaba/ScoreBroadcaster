package com.devhub.scored.features.match.viewmodel
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.devhub.scored.features.match.data.Match
import com.devhub.scored.features.match.data.MatchStatus
import com.devhub.scored.features.players.data.PlayerProfile
import com.devhub.scored.features.teams.data.SavedTeam
import com.devhub.scored.features.match.data.ScoredDatabase
import com.devhub.scored.features.scoring.data.MatchRepository
import com.devhub.scored.features.players.data.SavedPlayerRepository
import com.devhub.scored.features.teams.data.SavedTeamRepository
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    /** Persist a new saved team locally and mirror it to Supabase when signed in. */
    fun addSavedTeam(team: SavedTeam) {
        savedTeamRepository.addTeamWithRemote(team, currentUserId)
    }

    /** Remove a saved team by id. The StateFlow updates automatically via Room. */
    fun removeSavedTeam(id: String) {
        savedTeamRepository.removeTeam(id)
    }

    /** Update an existing saved team locally and mirror the change to Supabase when signed in. */
    fun updateSavedTeam(team: SavedTeam) {
        savedTeamRepository.updateTeamWithRemote(team, currentUserId)
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

    /** Update an existing saved player profile locally and mirror the change to Supabase when signed in. */
    fun updateSavedPlayer(player: PlayerProfile) {
        savedPlayerRepository.updatePlayerWithRemote(player, currentUserId)
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

    /**
     * Trigger the bidirectional teams sync for the signed-in [userId].
     *
     * Should be called after [syncPlayersForUser] so that local player profiles are
     * available for reconstructing team–player relationships when hydrating from remote.
     *
     * Safe to call multiple times; the sync strategy is idempotent.
     */
    fun syncTeamsForUser(userId: String) {
        savedTeamRepository.syncWithRemote(userId, savedPlayers.value)
    }

    /**
     * Trigger the bidirectional matches sync for the signed-in [userId].
     *
     * Should be called after [syncTeamsForUser] so teams are available when
     * matches are restored from remote.
     *
     * Safe to call multiple times; the sync strategy is idempotent.
     * Also registers [userId] so that subsequent [confirmMatch] calls automatically
     * mirror new matches to Supabase.
     */
    fun syncMatchesForUser(userId: String) {
        matchRepository.syncWithRemote(userId)
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
        val confirmed = match.copy(status = MatchStatus.NOT_STARTED)
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
     * and may still appear in [resumableMatch] if its status is [MatchStatus.IN_PROGRESS]
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

    /**
     * Delete the match identified by [matchId] from local storage and Supabase.
     *
     * If the match being deleted is the current active match, the active session is cleared
     * immediately so the Home screen does not display a stale banner.  The [matches]
     * StateFlow updates automatically via Room.
     */
    fun deleteMatch(matchId: String) {
        if (_activeMatch.value?.localId == matchId) {
            clearActiveMatch()
        }
        matchRepository.deleteMatch(matchId)
    }

    /**
     * Publish the match identified by [matchId].
     *
     * Generates a unique share code and updates both local Room storage and Supabase.
     * The [onResult] callback is invoked on the calling coroutine with the generated
     * share code on success, or `null` on failure.
     */
    fun publishMatch(matchId: String, onResult: (shareCode: String?) -> Unit) {
        viewModelScope.launch {
            val code = matchRepository.publishMatch(matchId)
            if (code != null && _activeMatch.value?.id == matchId) {
                _activeMatch.value = _activeMatch.value?.copy(
                    visibility = com.devhub.scored.features.match.data.MatchVisibility.PUBLISHED,
                    shareCode = code,
                    publishedAt = System.currentTimeMillis()
                )
            }
            onResult(code)
        }
    }
}
