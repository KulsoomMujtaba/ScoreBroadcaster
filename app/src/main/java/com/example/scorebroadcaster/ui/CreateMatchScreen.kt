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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var teamAName by remember { mutableStateOf("") }
    // Players pre-filled when a saved team is selected; empty for manually typed names
    var teamAPlayers by remember { mutableStateOf<List<Player>>(emptyList()) }

    // Team B state
    var teamBName by remember { mutableStateOf("") }
    var teamBPlayers by remember { mutableStateOf<List<Player>>(emptyList()) }

    var selectedFormat by remember { mutableStateOf(MatchFormat.T20) }
    var customOvers by remember { mutableStateOf("") }
    var tossWinnerIsA by remember { mutableStateOf(true) }
    var tossDecision by remember { mutableStateOf(TossDecision.BAT) }
    var formatMenuExpanded by remember { mutableStateOf(false) }

    val savedTeams by matchSessionViewModel.savedTeams.collectAsState()

    // Derived names used for toss labels and match creation
    val finalTeamAName = teamAName.trim()
    val finalTeamBName = teamBName.trim()
    val teamALabel = finalTeamAName.ifBlank { "Team A" }
    val teamBLabel = finalTeamBName.ifBlank { "Team B" }

    val teamAReady = teamAName.isNotBlank()
    val teamBReady = teamBName.isNotBlank()
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

        TeamSelectorField(
            label = "Team A name *",
            teamName = teamAName,
            onTeamNameChange = { teamAName = it; teamAPlayers = emptyList() },
            savedTeams = savedTeams,
            onTeamSelected = { saved ->
                teamAName = saved.name
                teamAPlayers = saved.players.map { it.copy() }
            },
            onNewTeamCreated = { team ->
                matchSessionViewModel.addSavedTeam(team)
                teamAName = team.name
                teamAPlayers = team.players.map { it.copy() }
            }
        )

        // ── Team B ────────────────────────────────────────────────────────────
        HorizontalDivider()
        Text("Team B", style = MaterialTheme.typography.titleMedium)

        TeamSelectorField(
            label = "Team B name *",
            teamName = teamBName,
            onTeamNameChange = { teamBName = it; teamBPlayers = emptyList() },
            savedTeams = savedTeams,
            onTeamSelected = { saved ->
                teamBName = saved.name
                teamBPlayers = saved.players.map { it.copy() }
            },
            onNewTeamCreated = { team ->
                matchSessionViewModel.addSavedTeam(team)
                teamBName = team.name
                teamBPlayers = team.players.map { it.copy() }
            }
        )

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
                val teamA = Team(name = finalTeamAName, players = teamAPlayers)
                val teamB = Team(name = finalTeamBName, players = teamBPlayers)
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
                matchSessionViewModel.setPendingMatch(match)
                onNavigateToPlayers()
            },
            enabled = canProceed,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Next: Add Players →", style = MaterialTheme.typography.titleMedium)
        }
    }
}

// =============================================================================
// Reusable searchable team selector field
// =============================================================================

/**
 * An editable dropdown field for selecting a team.
 *
 * - The user can type a team name freely (free-text match).
 * - As the user types, the dropdown filters saved teams by the entered text
 *   (case-insensitive).
 * - At the bottom of the dropdown, a "＋ Create new team" action opens
 *   [CreateSavedTeamDialog].  Once created the team is saved and auto-selected.
 * - Selecting an existing saved team fills the name and pre-populates players.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamSelectorField(
    label: String,
    teamName: String,
    onTeamNameChange: (String) -> Unit,
    savedTeams: List<SavedTeam>,
    onTeamSelected: (SavedTeam) -> Unit,
    onNewTeamCreated: (SavedTeam) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }

    // Filter saved teams by the currently typed text (case-insensitive)
    val filteredTeams = remember(teamName, savedTeams) {
        savedTeams.filter { it.name.contains(teamName, ignoreCase = true) }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = teamName,
            onValueChange = {
                onTeamNameChange(it)
                expanded = true
            },
            label = { Text(label) },
            singleLine = true,
            isError = teamName.isEmpty(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(type = MenuAnchorType.PrimaryEditable),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // Matching saved teams
            filteredTeams.forEach { team ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(team.name, style = MaterialTheme.typography.bodyMedium)
                            if (team.players.isNotEmpty()) {
                                Text(
                                    "${team.players.size} players",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    },
                    onClick = {
                        onTeamSelected(team)
                        expanded = false
                    }
                )
            }

            // Divider before the create action (only when there are matching teams)
            if (filteredTeams.isNotEmpty()) {
                HorizontalDivider()
            }

            // Always-visible "create new team" action
            DropdownMenuItem(
                text = {
                    Text(
                        "+ Create new team",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {
                    expanded = false
                    showCreateDialog = true
                }
            )
        }
    }

    if (showCreateDialog) {
        CreateSavedTeamDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { team ->
                onNewTeamCreated(team)
                showCreateDialog = false
            }
        )
    }
}

