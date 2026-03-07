package com.example.scorebroadcaster.repository

import com.example.scorebroadcaster.data.entity.PlayerProfile

/**
 * In-memory repository for private (reusable) player profiles.
 *
 * Follows the same singleton pattern as [SavedTeamRepository].
 * Will be backed by persistent storage (Room / DataStore) in a future phase.
 *
 * Architecture note:
 * - Only [PlayerProfile.playerSourceType] == PRIVATE profiles are stored here today.
 * - Future APP_USER profiles will be looked up via a network/auth-linked source and
 *   should NOT be stored here — add a separate remote-profile repository at that point.
 */
object SavedPlayerRepository {

    private val _players = mutableListOf<PlayerProfile>()

    val players: List<PlayerProfile>
        get() = _players.toList()

    fun addPlayer(player: PlayerProfile) {
        _players.add(player)
    }

    fun removePlayer(id: String) {
        _players.removeAll { it.id == id }
    }

    fun updatePlayer(player: PlayerProfile) {
        val index = _players.indexOfFirst { it.id == player.id }
        if (index >= 0) _players[index] = player
    }

    fun findById(id: String): PlayerProfile? = _players.find { it.id == id }
}
