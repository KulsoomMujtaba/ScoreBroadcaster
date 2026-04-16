package com.devhub.scored.features.players.ui
import com.devhub.scored.features.players.data.Player

// =============================================================================
// Cross-team player identity helpers
// =============================================================================

/**
 * Returns a normalised form of a player name for ad-hoc identity comparison.
 * Trimmed and lowercased so "Ahmed " and "ahmed" resolve to the same identity.
 */
fun normalizePlayerName(name: String): String = name.trim().lowercase()

/**
 * Returns true if [this] and [other] refer to the same player within one match.
 *
 * Identity rules (per spec):
 * 1. If both have a non-null [Player.sourceProfileId] → equal when IDs are equal.
 * 2. If either (or both) lack a [Player.sourceProfileId] → equal when normalised
 *    names are equal.
 */
fun Player.sameIdentityAs(other: Player): Boolean {
    val thisId = sourceProfileId
    val otherId = other.sourceProfileId
    return if (thisId != null && otherId != null) {
        thisId == otherId
    } else {
        normalizePlayerName(name) == normalizePlayerName(other.name)
    }
}

/**
 * Returns true if any player in [teamA] shares an identity with any player in [teamB].
 */
fun hasCrossTeamDuplicate(teamA: List<Player>, teamB: List<Player>): Boolean =
    teamA.any { a -> teamB.any { b -> a.sameIdentityAs(b) } }

/**
 * Returns the set of [Player] entries from [teamA] that also appear in [teamB]
 * (i.e. the conflict set as seen from Team A's perspective).
 */
fun crossTeamConflicts(teamA: List<Player>, teamB: List<Player>): Set<Player> =
    teamA.filter { a -> teamB.any { b -> a.sameIdentityAs(b) } }.toSet()
