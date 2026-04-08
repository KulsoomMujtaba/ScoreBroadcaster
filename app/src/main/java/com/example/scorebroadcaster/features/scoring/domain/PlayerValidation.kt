package com.example.scorebroadcaster.features.scoring.domain

import com.example.scorebroadcaster.features.match.data.Match
import com.example.scorebroadcaster.features.players.data.Player
import com.example.scorebroadcaster.features.players.ui.sameIdentityAs
import com.example.scorebroadcaster.features.teams.data.Team

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
