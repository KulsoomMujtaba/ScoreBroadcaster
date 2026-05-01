package com.devhub.scored.features.players.ui
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.devhub.scored.features.players.data.PlayerProfile
import com.devhub.scored.features.players.data.PlayerSourceType

/**
 * Reusable "Add Player" dialog used throughout the app wherever a single player must be chosen.
 *
 * Layout hierarchy:
 * 1. **Search** — unified search + create field ("Search or add player") at the top.
 * 2. **Dynamic "Add 'X'" row** — appears when the typed name doesn't match any saved player;
 *    tapping it opens the Quick Player / Save & Add options via [AddPlayerOptionsDialog].
 * 3. **My Players** — vertically scrollable [LazyColumn] (max 300 dp); one tap selects a player
 *    and closes the dialog. Shows an empty-state message when no eligible players exist.
 *
 * @param savedPlayers        Existing private player profiles (My Players) to display and search.
 * @param onDismiss           Called when the dialog is cancelled without a selection.
 * @param onSelect            Called with an *existing* [PlayerProfile] that was tapped.
 *                            The caller should **not** persist it again — it is already saved.
 * @param onCreateAndSelect   Called with a *newly-built* [PlayerProfile] when the scorer
 *                            chooses "Save & Add Player". The caller is responsible for persisting
 *                            the profile (e.g. via `MatchSessionViewModel.addSavedPlayer`).
 * @param onCreateQuick       Optional. Called with the player *name* when "Add as Quick Player" is
 *                            chosen. Quick players are not persisted. When null, the Quick Player
 *                            option is hidden and the dialog behaves as before (legacy mode).
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
    onCreateQuick: ((name: String) -> Unit)? = null,
    excludedProfileIds: Set<String> = emptySet(),
    excludedNames: Set<String> = emptySet()
) {
    // Unified search + new-player name field
    var searchQuery by remember { mutableStateOf("") }

    // When non-null, the AddPlayerOptionsDialog is shown for this player name
    var pendingCreateName by remember { mutableStateOf<String?>(null) }

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

    val searchTrimmed = searchQuery.trim()

    // Show "Add 'X'" row when there is text that doesn't exactly match any saved player,
    // and the name is not in the excluded set
    val showAddNewRow = searchTrimmed.isNotEmpty() &&
        eligible.none { it.displayName.equals(searchTrimmed, ignoreCase = true) } &&
        normalizePlayerName(searchTrimmed) !in excludedNames

    val nameConflictsWithOtherTeam = searchTrimmed.isNotEmpty() &&
        normalizePlayerName(searchTrimmed) in excludedNames

    // Options dialog for new-player creation (only shown when Quick Player support is enabled)
    val createName = pendingCreateName
    if (createName != null && onCreateQuick != null) {
        AddPlayerOptionsDialog(
            name = createName,
            onQuickAdd = {
                onCreateQuick(createName)
                pendingCreateName = null
            },
            onSaveAndAdd = {
                onCreateAndSelect(
                    PlayerProfile(
                        displayName = createName,
                        playerSourceType = PlayerSourceType.PRIVATE
                    )
                )
                pendingCreateName = null
            },
            onDismiss = { pendingCreateName = null }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Player") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // --- Unified search + create field ---
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search or add player") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    isError = nameConflictsWithOtherTeam
                )

                if (nameConflictsWithOtherTeam) {
                    Text(
                        text = "This player is already assigned to the other team.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // --- Player list (dynamic "Add 'X'" row + saved players) ---
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Dynamic "Add 'X'" row
                    if (showAddNewRow) {
                        item(key = "add_new_$searchTrimmed") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable {
                                        if (onCreateQuick != null) {
                                            // Show Quick/Save options dialog
                                            pendingCreateName = searchTrimmed
                                        } else {
                                            // Legacy: directly save & add without showing options
                                            onCreateAndSelect(
                                                PlayerProfile(
                                                    displayName = searchTrimmed,
                                                    playerSourceType = PlayerSourceType.PRIVATE
                                                )
                                            )
                                        }
                                    }
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.PersonAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = "Add \"$searchTrimmed\"",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (onCreateQuick != null) {
                                        Text(
                                            text = "Quick player or save to My Players",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Empty / no-results states
                    if (eligible.isEmpty() && !showAddNewRow) {
                        item(key = "empty_state") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "No saved players yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = "Type a name above to add your first player.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    } else if (filtered.isEmpty() && searchQuery.isNotBlank() && !showAddNewRow) {
                        item(key = "no_results") {
                            Text(
                                text = "No players found",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    } else {
                        if (filtered.isNotEmpty()) {
                            item(key = "my_players_header") {
                                LaunchedEffect(filtered.size) {
                                    Log.d("PlayerPickerDialog", "Displaying ${filtered.size} players in add-player UI")
                                }
                                Text(
                                    text = "My Players",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                                )
                            }
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
                                    Text(
                                        text = profile.displayName,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
