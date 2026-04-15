package com.example.scorebroadcaster.features.teams.ui
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.scorebroadcaster.features.players.data.Player
import com.example.scorebroadcaster.features.players.data.PlayerProfile
import com.example.scorebroadcaster.features.players.data.PlayerSourceType
import com.example.scorebroadcaster.features.teams.data.SavedTeam
import com.example.scorebroadcaster.features.match.viewmodel.MatchSessionViewModel
import com.example.scorebroadcaster.features.players.ui.MultiPlayerPickerSheet
import com.example.scorebroadcaster.features.teams.data.Team

@Composable
fun SavedTeamsScreen(
    matchSessionViewModel: MatchSessionViewModel,
    modifier: Modifier = Modifier
) {
    val savedTeams by matchSessionViewModel.savedTeams.collectAsState()
    val savedPlayers by matchSessionViewModel.savedPlayers.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  New Team")
            }
        }

        if (savedTeams.isEmpty()) {
            Spacer(Modifier.height(40.dp))
            Text(
                text = "No saved teams yet. Create one to reuse it when setting up a match.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        } else {
            savedTeams.forEach { team ->
                SavedTeamCard(
                    team = team,
                    onDelete = { matchSessionViewModel.removeSavedTeam(team.id) }
                )
            }
        }
    }

    if (showCreateDialog) {
        CreateSavedTeamDialog(
            savedPlayers = savedPlayers,
            onDismiss = { showCreateDialog = false },
            onConfirm = { team ->
                matchSessionViewModel.addSavedTeam(team)
                showCreateDialog = false
            },
            onCreatePlayer = { profile -> matchSessionViewModel.addSavedPlayer(profile) }
        )
    }
}

// =============================================================================
// Saved team card
// =============================================================================

@Composable
private fun SavedTeamCard(team: SavedTeam, onDelete: () -> Unit) {
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = team.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete ${team.name}",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (team.players.isEmpty()) {
                Text(
                    text = "No players",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            } else {
                Text(
                    text = team.players.joinToString("  ·  ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// =============================================================================
// Create saved team dialog
// =============================================================================

@Composable
fun CreateSavedTeamDialog(
    savedPlayers: List<PlayerProfile> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (SavedTeam) -> Unit,
    /** Called when a new private [PlayerProfile] is created inline so it can be persisted. */
    onCreatePlayer: (PlayerProfile) -> Unit = {}
) {
    var teamName by remember { mutableStateOf("") }
    // Ordered list of players confirmed via the bulk picker
    val selectedPlayers = remember { mutableStateListOf<Player>() }
    // Controls whether the bulk multi-select picker is open
    var showMultiPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Saved Team") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = teamName,
                    onValueChange = { teamName = it },
                    label = { Text("Team name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                HorizontalDivider()

                Text("Players", style = MaterialTheme.typography.labelMedium)

                OutlinedButton(
                    onClick = { showMultiPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("  Add from My Players")
                }

                if (selectedPlayers.isNotEmpty()) {
                    val count = selectedPlayers.size
                    Text(
                        text = "$count player${if (count == 1) "" else "s"} selected",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    selectedPlayers.forEachIndexed { index, player ->
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
                                onClick = { selectedPlayers.removeAt(index) },
                                modifier = Modifier.size(36.dp)
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
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(SavedTeam(name = teamName.trim(), players = selectedPlayers.toList()))
                },
                enabled = teamName.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    // Show MultiPlayerPickerSheet when "Add Players" is tapped
    if (showMultiPicker) {
        MultiPlayerPickerSheet(
            savedPlayers = savedPlayers,
            initiallySelectedIds = selectedPlayers.mapNotNull { it.sourceProfileId }.toSet(),
            onCreatePlayer = { name, saveToMyPlayers ->
                val profile = PlayerProfile(
                    displayName = name,
                    playerSourceType = PlayerSourceType.PRIVATE
                )
                if (saveToMyPlayers) {
                    onCreatePlayer(profile)
                }
                profile
            },
            onConfirm = { profiles ->
                selectedPlayers.clear()
                selectedPlayers.addAll(
                    profiles.map { profile ->
                        Player(name = profile.displayName, sourceProfileId = profile.id)
                    }
                )
                showMultiPicker = false
            },
            onDismiss = { showMultiPicker = false }
        )
    }
}
