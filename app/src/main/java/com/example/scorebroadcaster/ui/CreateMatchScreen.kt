package com.example.scorebroadcaster.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.scorebroadcaster.data.entity.Match
import com.example.scorebroadcaster.data.entity.MatchFormat
import com.example.scorebroadcaster.data.entity.Player
import com.example.scorebroadcaster.data.entity.SavedTeam
import com.example.scorebroadcaster.data.entity.Team
import com.example.scorebroadcaster.data.entity.TossDecision
import com.example.scorebroadcaster.viewmodel.MatchSessionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateMatchScreen(
    matchSessionViewModel: MatchSessionViewModel,
    onNavigateToPlayers: () -> Unit,
    modifier: Modifier = Modifier
) {
    var title by remember { mutableStateOf("") }

    // Team A state
    var teamAUseSaved by remember { mutableStateOf(false) }
    var teamAName by remember { mutableStateOf("") }
    var teamASelectedSaved by remember { mutableStateOf<SavedTeam?>(null) }
    // Players pre-filled from a saved team (empty when typing a new team name)
    var teamAPlayers by remember { mutableStateOf<List<Player>>(emptyList()) }
    var saveTeamA by remember { mutableStateOf(false) }

    // Team B state
    var teamBUseSaved by remember { mutableStateOf(false) }
    var teamBName by remember { mutableStateOf("") }
    var teamBSelectedSaved by remember { mutableStateOf<SavedTeam?>(null) }
    var teamBPlayers by remember { mutableStateOf<List<Player>>(emptyList()) }
    var saveTeamB by remember { mutableStateOf(false) }

    var selectedFormat by remember { mutableStateOf(MatchFormat.T20) }
    var customOvers by remember { mutableStateOf("") }
    var tossWinnerIsA by remember { mutableStateOf(true) }
    var tossDecision by remember { mutableStateOf(TossDecision.BAT) }
    var formatMenuExpanded by remember { mutableStateOf(false) }

    // Saved-team picker dialogs
    var showSavedTeamPickerForA by remember { mutableStateOf(false) }
    var showSavedTeamPickerForB by remember { mutableStateOf(false) }
    val savedTeams by matchSessionViewModel.savedTeams.collectAsState()

    // Derived names used for toss labels and match creation
    val finalTeamAName = if (teamAUseSaved) teamASelectedSaved?.name.orEmpty() else teamAName.trim()
    val finalTeamBName = if (teamBUseSaved) teamBSelectedSaved?.name.orEmpty() else teamBName.trim()
    val teamALabel = finalTeamAName.ifBlank { "Team A" }
    val teamBLabel = finalTeamBName.ifBlank { "Team B" }

    val teamAReady = if (teamAUseSaved) teamASelectedSaved != null else teamAName.isNotBlank()
    val teamBReady = if (teamBUseSaved) teamBSelectedSaved != null else teamBName.isNotBlank()
    val oversValue = if (selectedFormat == MatchFormat.CUSTOM) {
        customOvers.toIntOrNull() ?: 0
    } else {
        selectedFormat.defaultOvers
    }
    val customOversError = selectedFormat == MatchFormat.CUSTOM && oversValue <= 0

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Create Match", style = MaterialTheme.typography.headlineMedium)

        // Match title (optional)
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Match title (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )

        // ── Team A ────────────────────────────────────────────────────────────
        HorizontalDivider()
        Text("Team A", style = MaterialTheme.typography.titleMedium)

        // Mode selector: New Team | Use Saved Team
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !teamAUseSaved,
                onClick = {
                    teamAUseSaved = false
                    teamASelectedSaved = null
                    teamAPlayers = emptyList()
                },
                label = { Text("New Team") }
            )
            FilterChip(
                selected = teamAUseSaved,
                onClick = { teamAUseSaved = true },
                label = { Text("Use Saved Team") }
            )
        }

        if (!teamAUseSaved) {
            OutlinedTextField(
                value = teamAName,
                onValueChange = { teamAName = it },
                label = { Text("Team A name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = teamAName.isEmpty(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Checkbox(
                    checked = saveTeamA,
                    onCheckedChange = { saveTeamA = it }
                )
                Text(
                    "Save this team for future matches",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            when {
                savedTeams.isEmpty() -> Text(
                    "No saved teams yet. Create one from Saved Teams in the menu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                teamASelectedSaved == null -> OutlinedButton(
                    onClick = { showSavedTeamPickerForA = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Select a saved team…") }
                else -> teamASelectedSaved?.let { selected ->
                    SavedTeamChip(
                        team = selected,
                        onChangeTap = { showSavedTeamPickerForA = true }
                    )
                }
            }
        }

        // ── Team B ────────────────────────────────────────────────────────────
        HorizontalDivider()
        Text("Team B", style = MaterialTheme.typography.titleMedium)

        // Mode selector: New Team | Use Saved Team
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !teamBUseSaved,
                onClick = {
                    teamBUseSaved = false
                    teamBSelectedSaved = null
                    teamBPlayers = emptyList()
                },
                label = { Text("New Team") }
            )
            FilterChip(
                selected = teamBUseSaved,
                onClick = { teamBUseSaved = true },
                label = { Text("Use Saved Team") }
            )
        }

        if (!teamBUseSaved) {
            OutlinedTextField(
                value = teamBName,
                onValueChange = { teamBName = it },
                label = { Text("Team B name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = teamBName.isEmpty(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Checkbox(
                    checked = saveTeamB,
                    onCheckedChange = { saveTeamB = it }
                )
                Text(
                    "Save this team for future matches",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            when {
                savedTeams.isEmpty() -> Text(
                    "No saved teams yet. Create one from Saved Teams in the menu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                teamBSelectedSaved == null -> OutlinedButton(
                    onClick = { showSavedTeamPickerForB = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Select a saved team…") }
                else -> teamBSelectedSaved?.let { selected ->
                    SavedTeamChip(
                        team = selected,
                        onChangeTap = { showSavedTeamPickerForB = true }
                    )
                }
            }
        }

        HorizontalDivider()

        // Match format
        ExposedDropdownMenuBox(
            expanded = formatMenuExpanded,
            onExpandedChange = { formatMenuExpanded = it }
        ) {
            OutlinedTextField(
                value = selectedFormat.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("Format") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = formatMenuExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(
                expanded = formatMenuExpanded,
                onDismissRequest = { formatMenuExpanded = false }
            ) {
                MatchFormat.entries.forEach { format ->
                    DropdownMenuItem(
                        text = { Text(format.label) },
                        onClick = {
                            selectedFormat = format
                            formatMenuExpanded = false
                        }
                    )
                }
            }
        }

        if (selectedFormat == MatchFormat.CUSTOM) {
            OutlinedTextField(
                value = customOvers,
                onValueChange = { customOvers = it.filter { c -> c.isDigit() } },
                label = { Text("Number of overs *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = customOversError,
                supportingText = if (customOversError) {
                    { Text("Enter a valid number of overs") }
                } else null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                )
            )
        }

        // Toss winner
        Text("Toss won by", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = tossWinnerIsA,
                onClick = { tossWinnerIsA = true },
                label = { Text(teamALabel) }
            )
            FilterChip(
                selected = !tossWinnerIsA,
                onClick = { tossWinnerIsA = false },
                label = { Text(teamBLabel) }
            )
        }

        // Toss decision
        Text("Chose to", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TossDecision.entries.forEach { decision ->
                FilterChip(
                    selected = tossDecision == decision,
                    onClick = { tossDecision = decision },
                    label = { Text(decision.label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val canProceed = teamAReady && teamBReady && !customOversError

        Button(
            onClick = {
                val aPlayers = if (teamAUseSaved) teamAPlayers else emptyList()
                val bPlayers = if (teamBUseSaved) teamBPlayers else emptyList()
                val teamA = Team(name = finalTeamAName, players = aPlayers)
                val teamB = Team(name = finalTeamBName, players = bPlayers)
                val tossWinner = if (tossWinnerIsA) teamA else teamB
                val battingFirst = when {
                    tossDecision == TossDecision.BAT -> tossWinner
                    tossWinner.id == teamA.id -> teamB
                    else -> teamA
                }
                val bowlingFirst = if (battingFirst.id == teamA.id) teamB else teamA
                val match = Match(
                    title = title.trim(),
                    teamA = teamA,
                    teamB = teamB,
                    format = selectedFormat,
                    overs = oversValue,
                    tossWinner = tossWinner,
                    tossDecision = tossDecision,
                    battingFirst = battingFirst,
                    bowlingFirst = bowlingFirst
                )
                // Persist any newly created teams that the user marked for saving
                if (!teamAUseSaved && saveTeamA && teamAName.isNotBlank()) {
                    matchSessionViewModel.addSavedTeam(SavedTeam(name = teamAName.trim()))
                }
                if (!teamBUseSaved && saveTeamB && teamBName.isNotBlank()) {
                    matchSessionViewModel.addSavedTeam(SavedTeam(name = teamBName.trim()))
                }
                matchSessionViewModel.setPendingMatch(match)
                onNavigateToPlayers()
            },
            enabled = canProceed,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Next: Add Players →", style = MaterialTheme.typography.titleMedium)
        }
    }

    // Saved team pickers
    if (showSavedTeamPickerForA) {
        SavedTeamPickerDialog(
            savedTeams = savedTeams,
            onDismiss = { showSavedTeamPickerForA = false },
            onSelect = { saved ->
                teamASelectedSaved = saved
                // Copy players so the match is independent of the saved team template
                teamAPlayers = saved.players.map { it.copy() }
                showSavedTeamPickerForA = false
            }
        )
    }
    if (showSavedTeamPickerForB) {
        SavedTeamPickerDialog(
            savedTeams = savedTeams,
            onDismiss = { showSavedTeamPickerForB = false },
            onSelect = { saved ->
                teamBSelectedSaved = saved
                teamBPlayers = saved.players.map { it.copy() }
                showSavedTeamPickerForB = false
            }
        )
    }
}

// =============================================================================
// Selected saved-team chip – shown after a saved team has been chosen
// =============================================================================

@Composable
private fun SavedTeamChip(
    team: SavedTeam,
    onChangeTap: () -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(team.name, style = MaterialTheme.typography.bodyMedium)
                if (team.players.isNotEmpty()) {
                    val preview = team.players.take(3).joinToString(", ") { it.name }
                    val suffix = if (team.players.size > 3) ", …" else ""
                    Text(
                        "${team.players.size} players · $preview$suffix",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            TextButton(onClick = onChangeTap) { Text("Change") }
        }
    }
}

// =============================================================================
// Saved team picker dialog
// =============================================================================

@Composable
fun SavedTeamPickerDialog(
    savedTeams: List<SavedTeam>,
    onDismiss: () -> Unit,
    onSelect: (SavedTeam) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Saved Team") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                savedTeams.forEach { team ->
                    TextButton(
                        onClick = { onSelect(team) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = team.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (team.players.isNotEmpty()) {
                                Text(
                                    text = "${team.players.size} players",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
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

