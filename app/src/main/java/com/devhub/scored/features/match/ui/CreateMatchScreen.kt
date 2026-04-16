package com.devhub.scored.features.match.ui
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import com.devhub.scored.features.match.data.Match
import com.devhub.scored.features.match.data.MatchFormat
import com.devhub.scored.features.players.data.Player
import com.devhub.scored.features.players.data.PlayerProfile
import com.devhub.scored.features.teams.data.SavedTeam
import com.devhub.scored.features.teams.data.Team
import com.devhub.scored.features.match.data.TossDecision
import com.devhub.scored.features.match.viewmodel.MatchSessionViewModel
import com.devhub.scored.features.teams.ui.CreateSavedTeamDialog

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
    // Step index: 0 = Teams, 1 = Match Format, 2 = Toss
    var currentStep by remember { mutableStateOf(0) }

    // Team A state
    var teamAName by remember { mutableStateOf("") }
    // Players pre-filled when a saved team is selected; empty for a newly created team
    var teamAPlayers by remember { mutableStateOf<List<Player>>(emptyList()) }
    // Tracks which saved team is currently selected for Team A (null if none)
    var teamASelectedSaved by remember { mutableStateOf<SavedTeam?>(null) }

    // Team B state
    var teamBName by remember { mutableStateOf("") }
    var teamBPlayers by remember { mutableStateOf<List<Player>>(emptyList()) }
    // Tracks which saved team is currently selected for Team B (null if none)
    var teamBSelectedSaved by remember { mutableStateOf<SavedTeam?>(null) }

    var selectedFormat by remember { mutableStateOf(MatchFormat.T20) }
    var customOvers by remember { mutableStateOf("") }
    var tapeBallOvers by remember { mutableStateOf("") }
    var tossWinnerIsA by remember { mutableStateOf(true) }
    var tossDecision by remember { mutableStateOf(TossDecision.BAT) }

    val savedTeams by matchSessionViewModel.savedTeams.collectAsState()
    val savedPlayers by matchSessionViewModel.savedPlayers.collectAsState()

    // Derived names used for toss labels and match creation
    val finalTeamAName = teamAName.trim()
    val finalTeamBName = teamBName.trim()
    val teamALabel = finalTeamAName.ifBlank { "Team A" }
    val teamBLabel = finalTeamBName.ifBlank { "Team B" }

    val teamAReady = teamAName.isNotBlank()
    val teamBReady = teamBName.isNotBlank()
    val sameTeamError = teamAReady && teamBReady &&
            finalTeamAName.equals(finalTeamBName, ignoreCase = true)
    val oversValue = when (selectedFormat) {
        MatchFormat.CUSTOM -> customOvers.toIntOrNull() ?: 0
        MatchFormat.TAPE_BALL -> tapeBallOvers.toIntOrNull() ?: 0
        else -> selectedFormat.defaultOvers
    }
    val customOversError = selectedFormat == MatchFormat.CUSTOM && oversValue <= 0
    val tapeBallOversError = selectedFormat == MatchFormat.TAPE_BALL &&
            (oversValue <= 0 || oversValue >= 100)

    val stepLabels = listOf("Teams", "Match Format", "Toss")

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Step indicator
        Text(
            text = "Step ${currentStep + 1} of 3 — ${stepLabels[currentStep]}",
            style = MaterialTheme.typography.titleMedium
        )
        HorizontalDivider()

        when (currentStep) {
            // ── STEP 1: Teams ─────────────────────────────────────────────────
            0 -> {
                TeamSelectorCard(
                    label = "Team A",
                    teamName = teamAName,
                    playerCount = teamAPlayers.size,
                    savedTeams = savedTeams,
                    savedPlayers = savedPlayers,
                    onCreatePlayer = { profile -> matchSessionViewModel.addSavedPlayer(profile) },
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

                TeamSelectorCard(
                    label = "Team B",
                    teamName = teamBName,
                    playerCount = teamBPlayers.size,
                    savedTeams = savedTeams,
                    savedPlayers = savedPlayers,
                    onCreatePlayer = { profile -> matchSessionViewModel.addSavedPlayer(profile) },
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

                Button(
                    onClick = { currentStep = 1 },
                    enabled = teamAReady && teamBReady && !sameTeamError,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Next")
                }
            }

            // ── STEP 2: Match Format ──────────────────────────────────────────
            1 -> {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MatchFormat.entries.forEach { format ->
                        FilterChip(
                            selected = selectedFormat == format,
                            onClick = {
                                if (selectedFormat != format) {
                                    // Clear Tape Ball overs when switching away from that format
                                    if (format != MatchFormat.TAPE_BALL) {
                                        tapeBallOvers = ""
                                    }
                                    selectedFormat = format
                                    if (format == MatchFormat.TAPE_BALL) {
                                        Log.d("CreateMatch", "Tape Ball selected")
                                    }
                                }
                            },
                            label = { Text(FORMAT_CHIP_LABELS[format] ?: format.label) }
                        )
                    }
                }

                if (selectedFormat == MatchFormat.TAPE_BALL) {
                    OutlinedTextField(
                        value = tapeBallOvers,
                        onValueChange = { input ->
                            tapeBallOvers = input.filter { c -> c.isDigit() }
                        },
                        label = { Text("Enter number of overs *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = tapeBallOversError,
                        supportingText = if (tapeBallOversError) {
                            { Text("Enter a valid number of overs (1–99)") }
                        } else null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        )
                    )
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { currentStep = 0 },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Back")
                    }
                    Button(
                        onClick = {
                            if (selectedFormat == MatchFormat.TAPE_BALL) {
                                Log.d("CreateMatch", "Overs set to $oversValue")
                            }
                            currentStep = 2
                        },
                        enabled = !customOversError && !tapeBallOversError,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Next")
                    }
                }
            }

            // ── STEP 3: Toss ──────────────────────────────────────────────────
            2 -> {
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

                Text("Decision?", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TossDecision.entries.forEach { decision ->
                        FilterChip(
                            selected = tossDecision == decision,
                            onClick = { tossDecision = decision },
                            label = { Text(decision.label) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { currentStep = 1 },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Back")
                    }
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
                                title = "${teamA.name} vs ${teamB.name}",
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
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Create Match →", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

// =============================================================================
// Card-based team selector
// =============================================================================

/**
 * A tappable card that displays the currently selected team (name + player count)
 * or a "Select Team" prompt when nothing is chosen yet.
 *
 * Tapping anywhere on the card opens a dropdown listing all saved teams (excluding
 * the team already selected on the other side).  A "＋ Create new team" action at
 * the bottom of the dropdown opens [CreateSavedTeamDialog].
 *
 * Prevents selecting the same team twice via [excludedTeam].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamSelectorCard(
    label: String,
    teamName: String,
    playerCount: Int,
    savedTeams: List<SavedTeam>,
    savedPlayers: List<PlayerProfile> = emptyList(),
    onCreatePlayer: (PlayerProfile) -> Unit = {},
    onTeamSelected: (SavedTeam) -> Unit,
    onNewTeamCreated: (SavedTeam) -> Unit,
    excludedTeam: SavedTeam? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }

    // Saved teams available for selection: exclude the team already on the other side
    val availableTeams = remember(savedTeams, excludedTeam) {
        if (excludedTeam != null) savedTeams.filter { it.id != excludedTeam.id } else savedTeams
    }

    // True when the other side has taken the only saved team
    val noOtherTeamsAvailable = excludedTeam != null && availableTeams.isEmpty()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (teamName.isNotBlank()) {
                        Text(teamName, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = if (playerCount == 0) "No players added" else "$playerCount players",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Select Team",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
        }

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

            // Available saved teams
            availableTeams.forEach { team ->
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

            // Divider before the create action (only when there are teams to show)
            if (availableTeams.isNotEmpty()) {
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
            savedPlayers = savedPlayers,
            onDismiss = { showCreateDialog = false },
            onConfirm = { team ->
                onNewTeamCreated(team)
                showCreateDialog = false
            },
            onCreatePlayer = onCreatePlayer
        )
    }
}

