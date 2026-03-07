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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.scorebroadcaster.data.entity.PlayerProfile
import com.example.scorebroadcaster.data.entity.PlayerSourceType
import com.example.scorebroadcaster.viewmodel.MatchSessionViewModel

/**
 * Saved Players management screen.
 *
 * Lists all private [PlayerProfile] entries owned by the current user and allows
 * creating new ones or deleting existing ones.
 *
 * Architecture note:
 * - Only PRIVATE players are managed here today.
 * - Future APP_USER player lookup will be a separate flow (search existing app users).
 */
@Composable
fun SavedPlayersScreen(
    matchSessionViewModel: MatchSessionViewModel,
    modifier: Modifier = Modifier
) {
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
                Text("  New Player")
            }
        }

        if (savedPlayers.isEmpty()) {
            Spacer(Modifier.height(40.dp))
            Text(
                text = "No saved players yet. Create one to reuse them when setting up teams and matches.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        } else {
            HorizontalDivider()
            savedPlayers.forEach { profile ->
                SavedPlayerCard(
                    profile = profile,
                    onDelete = { matchSessionViewModel.removeSavedPlayer(profile.id) }
                )
            }
        }
    }

    if (showCreateDialog) {
        CreateSavedPlayerDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { profile ->
                matchSessionViewModel.addSavedPlayer(profile)
                showCreateDialog = false
            }
        )
    }
}

// =============================================================================
// Saved player card
// =============================================================================

@Composable
private fun SavedPlayerCard(profile: PlayerProfile, onDelete: () -> Unit) {
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = when (profile.playerSourceType) {
                        PlayerSourceType.PRIVATE -> "Private player"
                        PlayerSourceType.APP_USER -> "Linked to app account"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete ${profile.displayName}",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// =============================================================================
// Create saved player dialog
// =============================================================================

@Composable
fun CreateSavedPlayerDialog(
    onDismiss: () -> Unit,
    onConfirm: (PlayerProfile) -> Unit
) {
    var displayName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Player") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Player name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
                Text(
                    text = "This player will be saved privately and can be reused when creating teams or adding players to matches.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        PlayerProfile(
                            displayName = displayName.trim(),
                            playerSourceType = PlayerSourceType.PRIVATE
                        )
                    )
                },
                enabled = displayName.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
