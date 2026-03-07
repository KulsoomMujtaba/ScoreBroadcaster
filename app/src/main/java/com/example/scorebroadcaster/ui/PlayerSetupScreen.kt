package com.example.scorebroadcaster.ui

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.scorebroadcaster.data.entity.Player
import com.example.scorebroadcaster.data.entity.PlayerProfile
import com.example.scorebroadcaster.viewmodel.MatchSessionViewModel

@Composable
fun PlayerSetupScreen(
    matchSessionViewModel: MatchSessionViewModel,
    onNavigateToSummary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pending by matchSessionViewModel.pendingMatch.collectAsState()
    val match = pending ?: return
    val savedPlayers by matchSessionViewModel.savedPlayers.collectAsState()

    // Local state: mutable lists of player name strings for each team
    val teamAPlayers = remember { mutableStateListOf<String>().also { list ->
        match.teamA.players.forEach { list.add(it.name) }
        if (list.isEmpty()) list.add("")
    }}
    val teamBPlayers = remember { mutableStateListOf<String>().also { list ->
        match.teamB.players.forEach { list.add(it.name) }
        if (list.isEmpty()) list.add("")
    }}

    // Track the sourceProfileId for slots filled via PlayerPickerDialog so the
    // match-level Player snapshot preserves the link back to the reusable profile.
    val teamASourceIds = remember { hashMapOf<Int, String>() }
    val teamBSourceIds = remember { hashMapOf<Int, String>() }

    // Index of the slot that has the picker dialog open; null = closed
    // Negative indices represent team B: -(index+1)
    var pickerForIndex by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Add Players", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Enter player names for each team. You can add up to 11 players. " +
                    "Tap the person icon to pick from your saved players.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Team A
        Text(match.teamA.name, style = MaterialTheme.typography.titleMedium)
        PlayerListEditor(
            players = teamAPlayers,
            onPlayerChange = { index, value ->
                teamAPlayers[index] = value
                // Clear the profile link when the user manually edits the name
                teamASourceIds.remove(index)
            },
            onAddPlayer = { if (teamAPlayers.size < 11) teamAPlayers.add("") },
            onRemovePlayer = { index ->
                if (teamAPlayers.size > 1) {
                    teamAPlayers.removeAt(index)
                    teamASourceIds.remove(index)
                }
            },
            onPickSaved = { index -> pickerForIndex = index }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Team B
        Text(match.teamB.name, style = MaterialTheme.typography.titleMedium)
        PlayerListEditor(
            players = teamBPlayers,
            onPlayerChange = { index, value ->
                teamBPlayers[index] = value
                teamBSourceIds.remove(index)
            },
            onAddPlayer = { if (teamBPlayers.size < 11) teamBPlayers.add("") },
            onRemovePlayer = { index ->
                if (teamBPlayers.size > 1) {
                    teamBPlayers.removeAt(index)
                    teamBSourceIds.remove(index)
                }
            },
            onPickSaved = { index -> pickerForIndex = -(index + 1) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                // Build Player snapshots, preserving the sourceProfileId for slots that
                // were filled via PlayerPickerDialog.
                val updatedTeamA = match.teamA.copy(
                    players = teamAPlayers.mapIndexed { i, name -> Pair(i, name) }
                        .filter { (_, n) -> n.isNotBlank() }
                        .map { (origIdx, name) ->
                            Player(
                                name = name.trim(),
                                sourceProfileId = teamASourceIds[origIdx]
                            )
                        }
                )
                val updatedTeamB = match.teamB.copy(
                    players = teamBPlayers.mapIndexed { i, name -> Pair(i, name) }
                        .filter { (_, n) -> n.isNotBlank() }
                        .map { (origIdx, name) ->
                            Player(
                                name = name.trim(),
                                sourceProfileId = teamBSourceIds[origIdx]
                            )
                        }
                )
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
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Next: Match Summary →", style = MaterialTheme.typography.titleMedium)
        }
    }

    // Player picker dialog — opens when the person icon is tapped in any player slot.
    // Shown for both existing saved players (onSelect) and new inline creation (onCreateAndSelect).
    val pickerIdx = pickerForIndex
    if (pickerIdx != null) {
        val isTeamB = pickerIdx < 0
        val slotIndex = if (isTeamB) -(pickerIdx + 1) else pickerIdx

        fun applyProfile(profile: PlayerProfile) {
            if (isTeamB) {
                teamBPlayers[slotIndex] = profile.displayName
                teamBSourceIds[slotIndex] = profile.id
            } else {
                teamAPlayers[slotIndex] = profile.displayName
                teamASourceIds[slotIndex] = profile.id
            }
            pickerForIndex = null
        }

        PlayerPickerDialog(
            savedPlayers = savedPlayers,
            onDismiss = { pickerForIndex = null },
            onSelect = { profile -> applyProfile(profile) },
            onCreateAndSelect = { profile ->
                matchSessionViewModel.addSavedPlayer(profile)
                applyProfile(profile)
            }
        )
    }
}

// =============================================================================
// Player list editor
// =============================================================================

@Composable
private fun PlayerListEditor(
    players: List<String>,
    onPlayerChange: (Int, String) -> Unit,
    onAddPlayer: () -> Unit,
    onRemovePlayer: (Int) -> Unit,
    onPickSaved: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        players.forEachIndexed { index, name ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { onPlayerChange(index, it) },
                    label = { Text("Player ${index + 1}") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                // Always show the picker icon — tapping it opens PlayerPickerDialog which
                // supports both selecting saved players and creating a new one inline.
                IconButton(
                    onClick = { onPickSaved(index) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Pick or create player",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                if (players.size > 1) {
                    IconButton(
                        onClick = { onRemovePlayer(index) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove player",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        if (players.size < 11) {
            TextButton(
                onClick = onAddPlayer,
                modifier = Modifier.align(Alignment.Start)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text("  Add player")
            }
        }
    }
}


