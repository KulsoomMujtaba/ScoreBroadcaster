package com.example.scorebroadcaster.repository

import com.example.scorebroadcaster.data.entity.SavedTeam

/**
 * In-memory repository for saved (reusable) teams.
 * Will be backed by persistent storage in a future phase.
 */
object SavedTeamRepository {

    private val _teams = mutableListOf<SavedTeam>()

    val teams: List<SavedTeam>
        get() = _teams.toList()

    fun addTeam(team: SavedTeam) {
        _teams.add(team)
    }

    fun removeTeam(id: String) {
        _teams.removeAll { it.id == id }
    }

    fun updateTeam(team: SavedTeam) {
        val index = _teams.indexOfFirst { it.id == team.id }
        if (index >= 0) _teams[index] = team
    }
}
