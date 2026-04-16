package com.devhub.scored.features.scoring.domain

import android.util.Log
import com.devhub.scored.features.match.data.Match
import com.devhub.scored.features.players.data.Player
import com.devhub.scored.features.players.ui.sameIdentityAs
import com.devhub.scored.features.teams.data.Team

/**
 * Returns `true` if [player] can be added to [targetTeam] without creating a
 * cross-team conflict within [match].
 *
 * A player is blocked when the same identity already appears in the opposing team.
 * Identity is resolved via [Player.sameIdentityAs]: profile-ID comparison when both
 * players have a source-profile ID, name-based comparison otherwise.
 *
 * Logic:
 * 1. Determine the opponent team from [match] by comparing [targetTeam] ID against
 *    [Match.teamA] and [Match.teamB].
 * 2. If the player's identity is found in the opponent team → return `false`.
 * 3. Otherwise → return `true`.
 *
 * Unrecognised team IDs are handled gracefully by returning `true` (allow) so that
 * legacy or inconsistent data does not crash the scoring session.
 *
 * @param player     The player to be added.
 * @param targetTeam The team the player is being added to.
 * @param match      The match whose full rosters are checked.
 * @return `true` if the player may be added; `false` if they are already in the opposing team.
 */
fun canAddPlayerToTeam(player: Player, targetTeam: Team, match: Match): Boolean {
    val opponentTeam: Team = when (targetTeam.id) {
        match.teamA.id -> match.teamB
        match.teamB.id -> match.teamA
        else -> return true  // Unknown team — allow to avoid crash on legacy/inconsistent data
    }
    return opponentTeam.players.none { it.sameIdentityAs(player) }
}

/**
 * Returns the subset of [teamId]'s roster that is eligible for selection — i.e. players that
 * do not already appear in the opposing team.  Identity is resolved via [Player.sameIdentityAs].
 *
 * This is a **UX filtering** function used to hide invalid options before they are shown to
 * the scorer.  The defensive validation check ([canAddPlayerToTeam]) is still enforced at
 * assignment time and must not be removed.
 *
 * Logic:
 * 1. Resolve the current team and opposing team from [match] using [teamId].
 * 2. Filter out any player whose identity is already present in the opposing team.
 *
 * Unrecognised team IDs are handled gracefully by returning an empty list.
 *
 * @param teamId The ID of the team for which eligible players are requested.
 * @param match  The match containing both team rosters.
 * @return Filtered list of players eligible for selection.
 */
fun getEligiblePlayersForTeam(teamId: String, match: Match): List<Player> {
    val (currentTeam, opponentTeam) = when (teamId) {
        match.teamA.id -> Pair(match.teamA, match.teamB)
        match.teamB.id -> Pair(match.teamB, match.teamA)
        else -> {
            Log.w("PlayerValidation", "getEligiblePlayersForTeam: unrecognised teamId=$teamId")
            return emptyList()
        }
    }
    val eligible = currentTeam.players.filter { player ->
        opponentTeam.players.none { it.sameIdentityAs(player) }
    }
    val filteredOut = currentTeam.players.size - eligible.size
    Log.d("PlayerValidation", "Eligible players count: ${eligible.size}")
    if (filteredOut > 0) {
        Log.d("PlayerValidation", "Filtered out $filteredOut invalid players")
    }
    return eligible
}
