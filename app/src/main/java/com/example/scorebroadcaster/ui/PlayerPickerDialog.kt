package com.example.scorebroadcaster.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.scorebroadcaster.data.entity.PlayerProfile
import com.example.scorebroadcaster.data.entity.PlayerSourceType

/**
 * Reusable player picker dialog used throughout the app wherever a player must be chosen.
 *
 * Displays three sections:
 * 1. **Saved Players** — a searchable list of existing private [PlayerProfile] entries.
 *    One tap selects a player and closes the dialog.
 * 2. **Scored Users (coming soon)** — a clearly-labelled placeholder for future app-user
 *    search.  No backend is implemented yet; this section keeps the architecture
 *    future-friendly so the section can be activated without UI restructuring.
 * 3. **Create new player** — an inline text field + button that lets the scorer create a
 *    brand-new private player on the fly without navigating away.
 *
 * @param savedPlayers      Existing private player profiles to display and search.
 * @param onDismiss         Called when the dialog is cancelled without a selection.
 * @param onSelect          Called with an *existing* [PlayerProfile] that was tapped.
 *                          The caller should **not** persist it again — it is already saved.
 * @param onCreateAndSelect Called with a *newly-built* [PlayerProfile] when the scorer
 *                          types a name and taps the add button.  The caller is responsible
 *                          for persisting the profile (e.g. via
 *                          `MatchSessionViewModel.addSavedPlayer`).
 */
@Composable
fun PlayerPickerDialog(
    savedPlayers: List<PlayerProfile>,
    onDismiss: () -> Unit,
    onSelect: (PlayerProfile) -> Unit,
    onCreateAndSelect: (PlayerProfile) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var newPlayerName by remember { mutableStateOf("") }

    val filtered = remember(savedPlayers, searchQuery) {
        if (searchQuery.isBlank()) savedPlayers
        else savedPlayers.filter {
            it.displayName.contains(searchQuery.trim(), ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick Player") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // --- Search field ---
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search players") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                )

                // --- Saved Players section ---
                Text(
                    text = "Saved Players",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                if (filtered.isEmpty()) {
                    Text(
                        text = if (searchQuery.isBlank()) "No saved players yet."
                               else "No match for \"${searchQuery.trim()}\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                } else {
                    filtered.forEach { profile ->
                        OutlinedButton(
                            onClick = { onSelect(profile) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = profile.displayName,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                // --- Scored Users placeholder (future) ---
                Text(
                    text = "Scored Users  ·  coming soon",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Text(
                    text = "Search registered Scored accounts – not available yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                // --- Create new player section ---
                Text(
                    text = "Create new player",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newPlayerName,
                        onValueChange = { newPlayerName = it },
                        label = { Text("Player name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                    Button(
                        onClick = {
                            val name = newPlayerName.trim()
                            if (name.isNotEmpty()) {
                                onCreateAndSelect(
                                    PlayerProfile(
                                        displayName = name,
                                        playerSourceType = PlayerSourceType.PRIVATE
                                    )
                                )
                                newPlayerName = ""
                            }
                        },
                        enabled = newPlayerName.isNotBlank()
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Create player",
                            modifier = Modifier.size(18.dp)
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
