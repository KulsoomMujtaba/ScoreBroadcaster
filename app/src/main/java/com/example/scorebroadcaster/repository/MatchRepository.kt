package com.example.scorebroadcaster.repository

import com.example.scorebroadcaster.data.entity.Match

/**
 * In-memory local repository for matches.
 * Temporary implementation – will be replaced by a backend-backed repository in a future phase.
 */
object MatchRepository {

    private val _matches = mutableListOf<Match>()

    val matches: List<Match>
        get() = _matches.toList()

    private var _activeMatch: Match? = null

    val activeMatch: Match?
        get() = _activeMatch

    fun addMatch(match: Match) {
        _matches.add(match)
    }

    fun updateMatch(match: Match) {
        val index = _matches.indexOfFirst { it.id == match.id }
        if (index >= 0) {
            _matches[index] = match
        }
        if (_activeMatch?.id == match.id) {
            _activeMatch = match
        }
    }

    fun setActiveMatch(match: Match) {
        _activeMatch = match
    }

    fun clearActiveMatch() {
        _activeMatch = null
    }
}
