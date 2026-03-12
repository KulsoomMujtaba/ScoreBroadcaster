package com.example.scorebroadcaster.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.scorebroadcaster.data.entity.PlayerProfile
import com.example.scorebroadcaster.data.entity.PlayerSourceType

/**
 * Full-screen multi-select player picker for bulk team-building flows.
 *
 * Shows a searchable list of [savedPlayers] with checkboxes. The user can select
 * multiple players at once, create a new player inline, and confirm once.
 *
 * Use this for:
 * - [PlayerSetupScreen] "Pick from saved players" action
 * - [CreateSavedTeamDialog] "Add players" action
 *
 * Do NOT use for single-player selection during an active match (next batter, bowler change, etc.).
 *
 * @param savedPlayers          List of existing saved player profiles to display.
 * @param initiallySelectedIds  Profile IDs to pre-select when the picker opens.
 * @param maxSelectionCount     Maximum number of players that can be selected (default 11).
 * @param excludedPlayerIds     Profile IDs to hide from the list (already assigned elsewhere).
 * @param onCreatePlayer        Called with a player name; must return a persisted [PlayerProfile].
 * @param onConfirm             Called with the ordered list of selected profiles when confirmed.
 * @param onDismiss             Called when the picker is closed without confirming.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiPlayerPickerSheet(
    savedPlayers: List<PlayerProfile>,
    initiallySelectedIds: Set<String> = emptySet(),
    maxSelectionCount: Int = 11,
    excludedPlayerIds: Set<String> = emptySet(),
    onCreatePlayer: (String) -> PlayerProfile,
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

    var searchQuery by remember { mutableStateOf("") }
    var newPlayerName by remember { mutableStateOf("") }

    // Visible candidates: exclude players that are already in the excluded set
    val eligible = remember(savedPlayers, excludedPlayerIds) {
        savedPlayers.filter { it.id !in excludedPlayerIds }
    }

    val filtered = remember(eligible, searchQuery) {
        if (searchQuery.isBlank()) eligible
        else eligible.filter {
            it.displayName.contains(searchQuery.trim(), ignoreCase = true)
        }
    }

    val atLimit = selected.size >= maxSelectionCount

    fun togglePlayer(profile: PlayerProfile) {
        val existing = selected.indexOfFirst { it.id == profile.id }
        if (existing >= 0) {
            selected.removeAt(existing)
        } else if (!atLimit) {
            selected.add(profile)
        }
    }

    fun submitCreate() {
        val name = newPlayerName.trim()
        if (name.isEmpty()) return
        if (selected.size >= maxSelectionCount) return
        val profile = onCreatePlayer(name)
        if (selected.none { it.id == profile.id }) {
            selected.add(profile)
        }
        newPlayerName = ""
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
                        title = { Text("Select Players") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        },
                        actions = {
                            val label = if (selected.isEmpty()) "Add Selected"
                            else "Add ${selected.size} Player${if (selected.size == 1) "" else "s"}"
                            Button(
                                onClick = { onConfirm(selected.toList()) },
                                enabled = selected.isNotEmpty(),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(label)
                            }
                        }
                    )
                },
                bottomBar = {
                    // Sticky bottom: create new player + confirm
                    Surface(tonalElevation = 4.dp) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .imePadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HorizontalDivider()
                            Text(
                                text = "Create new player",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = newPlayerName,
                                    onValueChange = { newPlayerName = it },
                                    placeholder = { Text("Player name") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { submitCreate() }),
                                    enabled = !atLimit
                                )
                                OutlinedButton(
                                    onClick = { submitCreate() },
                                    enabled = newPlayerName.isNotBlank() && !atLimit
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text("  Create")
                                }
                            }
                            Button(
                                onClick = { onConfirm(selected.toList()) },
                                enabled = selected.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val label = if (selected.isEmpty()) "Add Selected"
                                else "Add ${selected.size} Player${if (selected.size == 1) "" else "s"}"
                                Text(label)
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp)
                        .fillMaxSize()
                ) {
                    Spacer(Modifier.height(8.dp))

                    // Search field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search players") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                    )

                    Spacer(Modifier.height(8.dp))

                    // Selected count + team limit info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selected.isNotEmpty()) {
                            Text(
                                text = "Selected: ${selected.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Box(Modifier)
                        }
                        Text(
                            text = "Team limit: $maxSelectionCount players",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    if (atLimit) {
                        Text(
                            text = "Maximum $maxSelectionCount players reached.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(4.dp))
                    }

                    // Player list or empty state
                    if (eligible.isEmpty()) {
                        Spacer(Modifier.height(32.dp))
                        Text(
                            text = "No saved players yet.\nCreate players below to start building your team.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    } else if (filtered.isEmpty()) {
                        Spacer(Modifier.height(32.dp))
                        Text(
                            text = "No saved players found",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(filtered, key = { it.id }) { profile ->
                                val isSelected = selected.any { it.id == profile.id }
                                val isDisabled = atLimit && !isSelected
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .clickable(enabled = !isDisabled) { togglePlayer(profile) }
                                        .padding(horizontal = 4.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { if (!isDisabled) togglePlayer(profile) },
                                        enabled = !isDisabled
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = profile.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isDisabled)
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                            else
                                                MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Saved player",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
