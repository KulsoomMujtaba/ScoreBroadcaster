package com.example.scorebroadcaster.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

/** Short chip labels used for format selection — avoids repeating the overs count in the chip. */
private val FORMAT_CHIP_LABELS = mapOf(
    MatchFormat.T20 to "T20",
    MatchFormat.T10 to "T10",
    MatchFormat.ODI to "ODI",
    MatchFormat.TAPE_BALL to "Tape Ball",
    MatchFormat.CUSTOM to "Custom"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    // Tracks which saved team is currently selected for Team A (null if free-typed)
    var teamASelectedSaved by remember { mutableStateOf<SavedTeam?>(null) }

    // Team B state
    var teamBName by remember { mutableStateOf("") }
    var teamBPlayers by remember { mutableStateOf<List<Player>>(emptyList()) }
    // Tracks which saved team is currently selected for Team B (null if free-typed)
    var teamBSelectedSaved by remember { mutableStateOf<SavedTeam?>(null) }

    var selectedFormat by remember { mutableStateOf(MatchFormat.T20) }
    var customOvers by remember { mutableStateOf("") }
    var tossWinnerIsA by remember { mutableStateOf(true) }
    var tossDecision by remember { mutableStateOf(TossDecision.BAT) }

    val savedTeams by matchSessionViewModel.savedTeams.collectAsState()

    // Derived names used for toss labels and match creation
    val finalTeamAName = teamAName.trim()
    val finalTeamBName = teamBName.trim()
    val teamALabel = finalTeamAName.ifBlank { "Team A" }
    val teamBLabel = finalTeamBName.ifBlank { "Team B" }

    val teamAReady = teamAName.isNotBlank()
    val teamBReady = teamBName.isNotBlank()
    val sameTeamError = teamAReady && teamBReady &&
            finalTeamAName.equals(finalTeamBName, ignoreCase = true)
    val oversValue = if (selectedFormat == MatchFormat.CUSTOM) {
        customOvers.toIntOrNull() ?: 0
    } else {
        selectedFormat.defaultOvers
    }
    val customOversError = selectedFormat == MatchFormat.CUSTOM && oversValue <= 0
    val canProceed = teamAReady && teamBReady && !customOversError && !sameTeamError

    // Summary line shown above the CTA button
    val formatSummary = FORMAT_CHIP_LABELS[selectedFormat] ?: selectedFormat.label
    val tossSummary = if (tossWinnerIsA) teamALabel else teamBLabel
    val decisionSummary = tossDecision.label.lowercase()
    val summaryText = "$teamALabel vs $teamBLabel\n$formatSummary • Toss: $tossSummary chose to $decisionSummary"

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

        // ── SECTION 1: Teams ──────────────────────────────────────────────────
        HorizontalDivider()
        Text("Teams", style = MaterialTheme.typography.titleMedium)

        TeamSelectorField(
            label = "Team A name *",
            teamName = teamAName,
            onTeamNameChange = { teamAName = it; teamAPlayers = emptyList(); teamASelectedSaved = null },
            savedTeams = savedTeams,
            excludedTeam = teamBSelectedSaved,
            onTeamSelected = { saved ->
                teamAName = saved.name
                teamAPlayers = saved.players.map { it.copy() }
                teamASelectedSaved = saved
            },
            onNewTeamCreated = { team ->
                matchSessionViewModel.addSavedTeam(team)
                teamAName = team.name
                teamAPlayers = team.players.map { it.copy() }
                teamASelectedSaved = team
            }
        )

        // Swap Teams button
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            IconButton(
                onClick = {
                    val tmpName = teamAName
                    val tmpPlayers = teamAPlayers
                    val tmpSaved = teamASelectedSaved
                    teamAName = teamBName
                    teamAPlayers = teamBPlayers
                    teamASelectedSaved = teamBSelectedSaved
                    teamBName = tmpName
                    teamBPlayers = tmpPlayers
                    teamBSelectedSaved = tmpSaved
                }
            ) {
                Icon(
                    imageVector = Icons.Default.SwapVert,
                    contentDescription = "Swap teams"
                )
            }
        }

        TeamSelectorField(
            label = "Team B name *",
            teamName = teamBName,
            onTeamNameChange = { teamBName = it; teamBPlayers = emptyList(); teamBSelectedSaved = null },
            savedTeams = savedTeams,
            excludedTeam = teamASelectedSaved,
            onTeamSelected = { saved ->
                teamBName = saved.name
                teamBPlayers = saved.players.map { it.copy() }
                teamBSelectedSaved = saved
            },
            onNewTeamCreated = { team ->
                matchSessionViewModel.addSavedTeam(team)
                teamBName = team.name
                teamBPlayers = team.players.map { it.copy() }
                teamBSelectedSaved = team
            }
        )

        if (sameTeamError) {
            Text(
                text = "Both teams cannot be the same.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        // ── SECTION 2: Match Format ───────────────────────────────────────────
        HorizontalDivider()
        Text("Match Format", style = MaterialTheme.typography.titleMedium)

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MatchFormat.entries.forEach { format ->
                FilterChip(
                    selected = selectedFormat == format,
                    onClick = { selectedFormat = format },
                    label = { Text(FORMAT_CHIP_LABELS[format] ?: format.label) }
                )
            }
        }

        if (selectedFormat == MatchFormat.CUSTOM) {
            OutlinedTextField(
                value = customOvers,
                onValueChange = { customOvers = it.filter { c -> c.isDigit() } },
                label = { Text("Overs per side *") },
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

        // ── SECTION 3: Toss ───────────────────────────────────────────────────
        HorizontalDivider()
        Text("Toss", style = MaterialTheme.typography.titleMedium)

        Text("Who won the toss?", style = MaterialTheme.typography.labelLarge)
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

        Text("Decision", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TossDecision.entries.forEach { decision ->
                FilterChip(
                    selected = tossDecision == decision,
                    onClick = { tossDecision = decision },
                    label = { Text(decision.label) }
                )
            }
        }

        // ── Summary ───────────────────────────────────────────────────────────
        HorizontalDivider()
        Text(
            text = summaryText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

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
    excludedTeam: SavedTeam? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }

    // Saved teams available for selection: exclude the team already selected on the other side
    val availableTeams = remember(savedTeams, excludedTeam) {
        if (excludedTeam != null) savedTeams.filter { it.id != excludedTeam.id } else savedTeams
    }

    // Further filter by the currently typed text (case-insensitive)
    val filteredTeams = remember(teamName, availableTeams) {
        availableTeams.filter { it.name.contains(teamName, ignoreCase = true) }
    }

    // True when the other side has selected a saved team and no other saved teams remain
    val noOtherTeamsAvailable = excludedTeam != null && availableTeams.isEmpty()

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
            // Info row when the other side has claimed the only saved team
            if (noOtherTeamsAvailable) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "No other saved teams available",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    },
                    onClick = {},
                    enabled = false
                )
                HorizontalDivider()
            }

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

