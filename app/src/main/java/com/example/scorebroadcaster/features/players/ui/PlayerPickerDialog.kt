package com.example.scorebroadcaster.features.players.ui
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.scorebroadcaster.features.players.data.PlayerProfile
import com.example.scorebroadcaster.features.players.data.PlayerSourceType
import com.example.scorebroadcaster.features.players.data.Player

/**
 * Reusable "Add Player" dialog used throughout the app wherever a player must be chosen.
 *
 * Layout hierarchy:
 * 1. **Search** — top search field that instantly filters the My Players list.
 * 2. **My Players** — vertically scrollable [LazyColumn] (max 300 dp); one tap selects a player
 *    and closes the dialog. Shows an empty-state message when no eligible players exist.
 * 3. **Quick Create** — always-visible inline text field + button to create a brand-new private
 *    player.
 *
 * @param savedPlayers        Existing private player profiles (My Players) to display and search.
 * @param onDismiss           Called when the dialog is cancelled without a selection.
 * @param onSelect            Called with an *existing* [PlayerProfile] that was tapped.
 *                            The caller should **not** persist it again — it is already saved.
 * @param onCreateAndSelect   Called with a *newly-built* [PlayerProfile] when the scorer
 *                            types a name and taps "Add Player".  The caller is responsible
 *                            for persisting the profile (e.g. via
 *                            `MatchSessionViewModel.addSavedPlayer`).
 * @param excludedProfileIds  Profile IDs that must not be shown (already assigned to the
 *                            other team via a saved profile).
 * @param excludedNames       Normalised (trimmed + lowercased) ad-hoc player names that
 *                            must not be shown (already assigned to the other team without
 *                            a saved profile).
 */
@Composable
fun PlayerPickerDialog(
    savedPlayers: List<PlayerProfile>,
    onDismiss: () -> Unit,
    onSelect: (PlayerProfile) -> Unit,
    onCreateAndSelect: (PlayerProfile) -> Unit,
    excludedProfileIds: Set<String> = emptySet(),
    excludedNames: Set<String> = emptySet()
) {
    var searchQuery by remember { mutableStateOf("") }
    var newPlayerName by remember { mutableStateOf("") }

    val eligible = remember(savedPlayers, excludedProfileIds, excludedNames) {
        savedPlayers.filter { profile ->
            profile.id !in excludedProfileIds &&
                normalizePlayerName(profile.displayName) !in excludedNames
        }
    }

    val filtered = remember(eligible, searchQuery) {
        if (searchQuery.isBlank()) eligible
        else eligible.filter {
            it.displayName.contains(searchQuery.trim(), ignoreCase = true)
        }
    }

    val hasSavedPlayers = eligible.isNotEmpty()

    val newNameConflicts = remember(newPlayerName, excludedNames) {
        newPlayerName.isNotBlank() &&
            normalizePlayerName(newPlayerName) in excludedNames
    }

    fun submitCreate() {
        val name = newPlayerName.trim()
        if (name.isNotEmpty() && !newNameConflicts) {
            onCreateAndSelect(
                PlayerProfile(
                    displayName = name,
                    playerSourceType = PlayerSourceType.PRIVATE
                )
            )
            newPlayerName = ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Player") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // --- Section 1: Search (only when saved players exist) ---
                if (hasSavedPlayers) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search players...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                    )
                }

                // --- Section 2: My Players ---
                if (!hasSavedPlayers) {
                    // Empty state: no players in My Players yet
                    Text(
                        text = "No players available. Create a new player below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                } else if (filtered.isEmpty()) {
                    Text(
                        text = "No players found",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                } else {
                    LaunchedEffect(filtered.size) {
                        Log.d("PlayerPickerDialog", "Displaying ${filtered.size} players in add-player UI")
                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filtered, key = { it.id }) { profile ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable { onSelect(profile) }
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Column {
                                    Text(
                                        text = profile.displayName,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "My Players",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }

                // --- Section 3: Quick Create ---
                HorizontalDivider()
                Text(
                    text = "Create new player",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                OutlinedTextField(
                    value = newPlayerName,
                    onValueChange = { newPlayerName = it },
                    placeholder = { Text("Player name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitCreate() }),
                    isError = newNameConflicts
                )
                if (newNameConflicts) {
                    Text(
                        text = "This player is already assigned to the other team.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Button(
                    onClick = { submitCreate() },
                    enabled = newPlayerName.isNotBlank() && !newNameConflicts,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Player")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
