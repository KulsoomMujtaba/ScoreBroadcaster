package com.example.scorebroadcaster.repository

import com.example.scorebroadcaster.data.entity.Match
import com.example.scorebroadcaster.data.entity.MatchVisibility

/**
 * In-memory local repository for matches.
 *
 * Current state: all matches are stored locally on the scoring device.
 *
 * Future readiness:
 * - [Match.localId] is the stable on-device key used here.
 * - [Match.remoteId] will be populated by a backend sync layer once publishing is implemented.
 * - [Match.visibility] controls whether viewers can see a match; only [MatchVisibility.PRIVATE]
 *   matches are stored here today.  A future remote repository will handle published matches.
 * - [Match.ownerUserId] will identify the scorer/owner once authentication is added; queries
 *   on this field are intentionally deferred to the remote layer.
 *
 * Architecture note – one-scorer / many-viewers:
 * Every match has exactly one scorer (the device that calls [addMatch]).  Viewer access will
 * be mediated by the remote backend and gated on [MatchVisibility]; the local repository
 * never needs to enforce viewer permissions.
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
