package com.example.scorebroadcaster.features.match.ui
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.scorebroadcaster.features.players.data.Player
import com.example.scorebroadcaster.features.players.data.PlayerProfile
import com.example.scorebroadcaster.features.players.data.PlayerSourceType
import com.example.scorebroadcaster.features.match.viewmodel.MatchSessionViewModel
import com.example.scorebroadcaster.features.match.data.Match
import com.example.scorebroadcaster.features.players.ui.MultiPlayerPickerSheet
import com.example.scorebroadcaster.features.players.ui.hasCrossTeamDuplicate

@Composable
fun PlayerSetupScreen(
    matchSessionViewModel: MatchSessionViewModel,
    onNavigateToSummary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pending by matchSessionViewModel.pendingMatch.collectAsState()
    val match = pending ?: return
    val savedPlayers by matchSessionViewModel.savedPlayers.collectAsState()

    // Local state: mutable lists of Player snapshots for each team
    val teamAPlayers = remember {
        mutableStateListOf<Player>().also { list ->
            match.teamA.players.forEach { list.add(it) }
        }
    }
    val teamBPlayers = remember {
        mutableStateListOf<Player>().also { list ->
            match.teamB.players.forEach { list.add(it) }
        }
    }

    // null = closed; true = multi-picker for Team A; false = multi-picker for Team B
    var multiPickerForTeamA by remember { mutableStateOf<Boolean?>(null) }

    // ---------------------------------------------------------------------------
    // Cross-team exclusion sets (recomputed whenever either roster changes)
    // ---------------------------------------------------------------------------

    // Profile IDs currently in Team B — used to exclude from Team A picker
    val excludedForA_ProfileIds by remember {
        derivedStateOf { teamBPlayers.mapNotNull { it.sourceProfileId }.toSet() }
    }
    // Profile IDs currently in Team A — used to exclude from Team B picker
    val excludedForB_ProfileIds by remember {
        derivedStateOf { teamAPlayers.mapNotNull { it.sourceProfileId }.toSet() }
    }

    // Cross-team conflict: any player appearing in both rosters
    val hasCrossTeamConflict by remember {
        derivedStateOf { hasCrossTeamDuplicate(teamAPlayers, teamBPlayers) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Add Players", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Select players for each team using the Add Players button.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Team A
        Text(match.teamA.name, style = MaterialTheme.typography.titleMedium)
        TeamPlayerSection(
            players = teamAPlayers,
            onAddPlayers = { multiPickerForTeamA = true },
            onRemovePlayer = { index -> teamAPlayers.removeAt(index) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Team B
        Text(match.teamB.name, style = MaterialTheme.typography.titleMedium)
        TeamPlayerSection(
            players = teamBPlayers,
            onAddPlayers = { multiPickerForTeamA = false },
            onRemovePlayer = { index -> teamBPlayers.removeAt(index) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val updatedTeamA = match.teamA.copy(players = teamAPlayers.toList())
                val updatedTeamB = match.teamB.copy(players = teamBPlayers.toList())
                val updatedMatch = match.copy(
                    teamA = updatedTeamA,
                    teamB = updatedTeamB,
                    battingFirst = if (match.battingFirst.id == match.teamA.id) updatedTeamA else updatedTeamB,
                    bowlingFirst = if (match.bowlingFirst.id == match.teamA.id) updatedTeamA else updatedTeamB,
                    tossWinner = if (match.tossWinner.id == match.teamA.id) updatedTeamA else updatedTeamB
                )
                matchSessionViewModel.setPendingMatch(updatedMatch)
                onNavigateToSummary()
            },
            enabled = !hasCrossTeamConflict,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Next: Match Summary →", style = MaterialTheme.typography.titleMedium)
        }
        if (hasCrossTeamConflict) {
            Text(
                text = "A player cannot be assigned to both teams in the same match.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Multi-select player picker — opens when "Add Players" is tapped for a team.
    val isPickingForTeamA = multiPickerForTeamA
    if (isPickingForTeamA != null) {
        val currentPlayers = if (isPickingForTeamA) teamAPlayers else teamBPlayers
        val otherTeamExcluded = if (isPickingForTeamA) excludedForA_ProfileIds else excludedForB_ProfileIds
        // Pre-select players already in this team (so they appear checked when re-opening picker)
        val initiallySelectedIds = currentPlayers.mapNotNull { it.sourceProfileId }.toSet()

        MultiPlayerPickerSheet(
            savedPlayers = savedPlayers,
            initiallySelectedIds = initiallySelectedIds,
            excludedPlayerIds = otherTeamExcluded,
            onCreatePlayer = { name, saveToMyPlayers ->
                val profile = PlayerProfile(
                    displayName = name,
                    playerSourceType = PlayerSourceType.PRIVATE
                )
                if (saveToMyPlayers) {
                    matchSessionViewModel.addSavedPlayer(profile)
                }
                profile
            },
            onConfirm = { profiles ->
                val teamLabel = if (isPickingForTeamA) "Team A" else "Team B"
                if (isPickingForTeamA) {
                    teamAPlayers.clear()
                    teamAPlayers.addAll(
                        profiles.map { Player(name = it.displayName, sourceProfileId = it.id) }
                    )
                    Log.d("PlayerSetup", "Player added to team: $teamLabel")
                    Log.d("PlayerSetup", "Current team size: ${teamAPlayers.size}")
                } else {
                    teamBPlayers.clear()
                    teamBPlayers.addAll(
                        profiles.map { Player(name = it.displayName, sourceProfileId = it.id) }
                    )
                    Log.d("PlayerSetup", "Player added to team: $teamLabel")
                    Log.d("PlayerSetup", "Current team size: ${teamBPlayers.size}")
                }
                multiPickerForTeamA = null
            },
            onDismiss = { multiPickerForTeamA = null }
        )
    }
}

// =============================================================================
// Team player section — shows Add Players button + selected player list
// =============================================================================

@Composable
private fun TeamPlayerSection(
    players: List<Player>,
    onAddPlayers: () -> Unit,
    onRemovePlayer: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onAddPlayers,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text("  Add Players")
        }

        if (players.isNotEmpty()) {
            val count = players.size
            Text(
                text = "$count player${if (count == 1) "" else "s"} selected",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            players.forEachIndexed { index, player ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = player.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onRemovePlayer(index) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove ${player.name}",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
