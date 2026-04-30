package com.devhub.scored.features.players.ui
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.devhub.scored.features.players.data.PlayerProfile

/**
 * Full-screen player picker for bulk team-building flows.
 *
 * Supports two player creation modes:
 * - **Quick Player**: a local-only player attached to this match (not saved to the database).
 * - **Saved Player**: persisted to My Players and reusable across matches.
 *
 * Layout:
 * 1. Search bar ("Search or add player") at the top — doubles as the new-player name input.
 * 2. Saved players list with individual [Add] / [Added ✓] buttons.
 * 3. When the search text does not match any saved player a dynamic "Add 'X'" row appears,
 *    opening [AddPlayerOptionsDialog] on tap.
 * 4. Selected-player chips in a horizontal scroll row below the search field.
 * 5. "Done" confirm button in the top-bar.
 *
 * Use this for:
 * - [PlayerSetupScreen] "Add Players" action
 * - [CreateSavedTeamDialog] "Add Players" action
 *
 * Do NOT use for single-player selection during an active match (next batter, bowler change, etc.).
 *
 * @param savedPlayers          List of existing My Players profiles to display.
 * @param initiallySelectedIds  Profile IDs to pre-select when the picker opens.
 * @param maxSelectionCount     Maximum number of players that can be selected. Defaults to
 *                              [Int.MAX_VALUE] (no limit). Pass a positive integer to cap selection.
 * @param excludedPlayerIds     Profile IDs to hide from the list (already assigned elsewhere).
 * @param onCreatePlayer        Called with a player name and a saveToMyPlayers flag; must return a [PlayerProfile].
 * @param onConfirm             Called with the ordered list of selected profiles when confirmed.
 * @param onDismiss             Called when the picker is closed without confirming.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiPlayerPickerSheet(
    savedPlayers: List<PlayerProfile>,
    initiallySelectedIds: Set<String> = emptySet(),
    maxSelectionCount: Int = Int.MAX_VALUE,
    excludedPlayerIds: Set<String> = emptySet(),
    onCreatePlayer: (name: String, saveToMyPlayers: Boolean) -> PlayerProfile,
    onConfirm: (List<PlayerProfile>) -> Unit,
    onDismiss: () -> Unit
) {
    // Ordered list of selected profiles — order reflects tap order
    val selected = remember {
        mutableStateListOf<PlayerProfile>().also { list ->
            savedPlayers
                .filter { it.id in initiallySelectedIds }
                .forEach { list.add(it) }
        }
    }

    // Profiles created in this picker session that may not yet be in savedPlayers (Room async).
    // They are merged with savedPlayers so the player appears in the list immediately.
    val pendingProfiles = remember { mutableStateListOf<PlayerProfile>() }

    // Unified search + new-player name field
    var searchQuery by remember { mutableStateOf("") }

    // When non-null, the AddPlayerOptionsDialog is shown for this player name
    var pendingCreateName by remember { mutableStateOf<String?>(null) }

    // Merge savedPlayers with any locally-created profiles not yet reflected in the Room Flow.
    val knownIds = savedPlayers.map { it.id }.toSet()
    val allProfiles = savedPlayers + pendingProfiles.filter { it.id !in knownIds }

    // Visible candidates: exclude players that are already in the excluded set
    val eligible = allProfiles.filter { it.id !in excludedPlayerIds }

    val filtered = if (searchQuery.isBlank()) eligible
        else eligible.filter {
            it.displayName.contains(searchQuery.trim(), ignoreCase = true)
        }

    val atLimit = selected.size >= maxSelectionCount

    // True when the typed name doesn't exactly match any saved player — show "Add 'X'" row
    val showAddNewRow = searchQuery.isNotBlank() &&
        eligible.none { it.displayName.equals(searchQuery.trim(), ignoreCase = true) }

    fun addProfile(profile: PlayerProfile) {
        if (pendingProfiles.none { it.id == profile.id }) {
            pendingProfiles.add(profile)
        }
        if (selected.none { it.id == profile.id }) {
            selected.add(profile)
        }
    }

    fun togglePlayer(profile: PlayerProfile) {
        val existing = selected.indexOfFirst { it.id == profile.id }
        if (existing >= 0) {
            selected.removeAt(existing)
        } else if (!atLimit) {
            selected.add(profile)
        }
    }

    // Options dialog: shown when "Add 'X'" is tapped
    val createName = pendingCreateName
    if (createName != null) {
        AddPlayerOptionsDialog(
            name = createName,
            onQuickAdd = {
                val profile = onCreatePlayer(createName, false)
                addProfile(profile)
                pendingCreateName = null
                searchQuery = ""
            },
            onSaveAndAdd = {
                val profile = onCreatePlayer(createName, true)
                addProfile(profile)
                pendingCreateName = null
                searchQuery = ""
            },
            onDismiss = { pendingCreateName = null }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Add Players") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        },
                        actions = {
                            val label = if (selected.isEmpty()) "Done"
                            else "Done (${selected.size})"
                            Button(
                                onClick = { onConfirm(selected.toList()) },
                                enabled = selected.isNotEmpty(),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(label)
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp)
                        .imePadding()
                        .fillMaxSize()
                ) {
                    Spacer(Modifier.height(8.dp))

                    // Unified search + create field
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
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                    )

                    Spacer(Modifier.height(8.dp))

                    // Selected players summary: count label + horizontally scrollable chips
                    if (selected.isNotEmpty()) {
                        Text(
                            text = "Selected: ${selected.size} player${if (selected.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            selected.toList().forEach { profile ->
                                InputChip(
                                    selected = true,
                                    onClick = { togglePlayer(profile) },
                                    label = { Text(profile.displayName) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove ${profile.displayName}",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    // Team limit info row (shown only when a finite cap is configured)
                    if (maxSelectionCount != Int.MAX_VALUE) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Maximum: $maxSelectionCount players",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        if (atLimit) {
                            Text(
                                text = "Maximum $maxSelectionCount players per team.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                    }

                    // Player list
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Dynamic "Add 'X'" row at the top of the list
                        if (showAddNewRow) {
                            item(key = "add_new_${searchQuery}") {
                                AddNewPlayerRow(
                                    name = searchQuery.trim(),
                                    enabled = !atLimit,
                                    onClick = {
                                        if (!atLimit) pendingCreateName = searchQuery.trim()
                                    }
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }

                        if (eligible.isEmpty() && !showAddNewRow) {
                            item(key = "empty_state") {
                                Spacer(Modifier.height(32.dp))
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "No saved players yet.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = "Type a name above to add your first player.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        } else if (filtered.isEmpty() && searchQuery.isNotBlank()) {
                            item(key = "no_results") {
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = "No saved players match \"${searchQuery.trim()}\".",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        } else {
                            if (filtered.isNotEmpty()) {
                                item(key = "my_players_header") {
                                    Text(
                                        text = "My Players",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                    )
                                }
                            }
                            items(filtered, key = { it.id }) { profile ->
                                val isSelected = selected.any { it.id == profile.id }
                                val isDisabled = atLimit && !isSelected
                                SavedPlayerRow(
                                    profile = profile,
                                    isSelected = isSelected,
                                    isDisabled = isDisabled,
                                    onToggle = { togglePlayer(profile) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// Sub-composables
// =============================================================================

/**
 * Dynamic "Add 'X'" row shown when the search text doesn't match any saved player.
 * Tapping it opens the Quick/Save options dialog.
 */
@Composable
private fun AddNewPlayerRow(
    name: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Default.PersonAdd,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (enabled) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Add \"$name\"",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Text(
                text = "Quick player or save to My Players",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * A row for a single saved player, showing an "Add" or "Added ✓" button.
 */
@Composable
private fun SavedPlayerRow(
    profile: PlayerProfile,
    isSelected: Boolean,
    isDisabled: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = profile.displayName,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.weight(1f),
            color = if (isDisabled)
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            else
                MaterialTheme.colorScheme.onSurface
        )
        if (isSelected) {
            TextButton(
                onClick = onToggle,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Added ✓", style = MaterialTheme.typography.labelMedium)
            }
        } else {
            OutlinedButton(
                onClick = onToggle,
                enabled = !isDisabled,
                modifier = Modifier.heightIn(min = 36.dp)
            ) {
                Text("Add")
            }
        }
    }
}

/**
 * Dialog shown when the user taps "Add 'X'" — presents two mutually exclusive options:
 * 1. **Add as Quick Player** — local only, not persisted.
 * 2. **Save & Add Player** — persisted to My Players.
 */
@Composable
internal fun AddPlayerOptionsDialog(
    name: String,
    onQuickAdd: () -> Unit,
    onSaveAndAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add \"$name\"") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Quick players are only available for this match.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                // Quick Player option
                OutlinedButton(
                    onClick = onQuickAdd,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Add as Quick Player",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Not saved — for this match only",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                // Save & Add option
                Button(
                    onClick = onSaveAndAdd,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Save & Add Player",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Saved to your player list for future matches",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
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
