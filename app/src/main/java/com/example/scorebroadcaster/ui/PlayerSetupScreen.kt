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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.scorebroadcaster.data.entity.Player
import com.example.scorebroadcaster.viewmodel.MatchSessionViewModel

@Composable
fun PlayerSetupScreen(
    matchSessionViewModel: MatchSessionViewModel,
    onNavigateToSummary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pending by matchSessionViewModel.pendingMatch.collectAsState()
    val match = pending ?: return

    // Local state: mutable lists of player name strings for each team
    val teamAPlayers = remember { mutableStateListOf<String>().also { list ->
        match.teamA.players.forEach { list.add(it.name) }
        if (list.isEmpty()) list.add("")
    }}
    val teamBPlayers = remember { mutableStateListOf<String>().also { list ->
        match.teamB.players.forEach { list.add(it.name) }
        if (list.isEmpty()) list.add("")
    }}

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Add Players", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Enter player names for each team. You can add up to 11 players.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Team A
        Text(match.teamA.name, style = MaterialTheme.typography.titleMedium)
        PlayerListEditor(
            players = teamAPlayers,
            onPlayerChange = { index, value -> teamAPlayers[index] = value },
            onAddPlayer = { if (teamAPlayers.size < 11) teamAPlayers.add("") },
            onRemovePlayer = { index -> if (teamAPlayers.size > 1) teamAPlayers.removeAt(index) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Team B
        Text(match.teamB.name, style = MaterialTheme.typography.titleMedium)
        PlayerListEditor(
            players = teamBPlayers,
            onPlayerChange = { index, value -> teamBPlayers[index] = value },
            onAddPlayer = { if (teamBPlayers.size < 11) teamBPlayers.add("") },
            onRemovePlayer = { index -> if (teamBPlayers.size > 1) teamBPlayers.removeAt(index) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val updatedTeamA = match.teamA.copy(
                    players = teamAPlayers
                        .filter { it.isNotBlank() }
                        .mapIndexed { i, name -> Player(name = name.trim().ifBlank { "Player ${i + 1}" }) }
                )
                val updatedTeamB = match.teamB.copy(
                    players = teamBPlayers
                        .filter { it.isNotBlank() }
                        .mapIndexed { i, name -> Player(name = name.trim().ifBlank { "Player ${i + 1}" }) }
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
}

@Composable
private fun PlayerListEditor(
    players: List<String>,
    onPlayerChange: (Int, String) -> Unit,
    onAddPlayer: () -> Unit,
    onRemovePlayer: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        players.forEachIndexed { index, name ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { onPlayerChange(index, it) },
                    label = { Text("Player ${index + 1}") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
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
