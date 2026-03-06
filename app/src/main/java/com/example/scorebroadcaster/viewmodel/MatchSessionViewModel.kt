package com.example.scorebroadcaster.viewmodel

import androidx.lifecycle.ViewModel
import com.example.scorebroadcaster.data.entity.Match
import com.example.scorebroadcaster.data.entity.MatchStatus
import com.example.scorebroadcaster.repository.MatchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the higher-level match lifecycle: creation, player setup, active session, and match list.
 * Works alongside [MatchViewModel], which handles ball-by-ball scoring events.
 */
class MatchSessionViewModel : ViewModel() {

    private val _matches = MutableStateFlow<List<Match>>(MatchRepository.matches)
    val matches: StateFlow<List<Match>> = _matches.asStateFlow()

    private val _activeMatch = MutableStateFlow<Match?>(MatchRepository.activeMatch)
    val activeMatch: StateFlow<Match?> = _activeMatch.asStateFlow()

    /** Draft match being assembled across the Create → Players → Summary flow. */
    private val _pendingMatch = MutableStateFlow<Match?>(null)
    val pendingMatch: StateFlow<Match?> = _pendingMatch.asStateFlow()

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

    /** Refresh state from the underlying repository (e.g. after returning to My Matches). */
    fun refresh() {
        _matches.value = MatchRepository.matches
        _activeMatch.value = MatchRepository.activeMatch
    }
}
