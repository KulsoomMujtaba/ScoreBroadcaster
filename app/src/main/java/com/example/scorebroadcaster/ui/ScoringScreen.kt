package com.example.scorebroadcaster.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.scorebroadcaster.data.InningsPhase
import com.example.scorebroadcaster.data.MatchState
import com.example.scorebroadcaster.data.PendingAction
import com.example.scorebroadcaster.data.ScoreEvent
import com.example.scorebroadcaster.data.ScoringConsoleState
import com.example.scorebroadcaster.data.entity.BattingEntry
import com.example.scorebroadcaster.data.entity.BowlingEntry
import com.example.scorebroadcaster.data.entity.DismissalDetail
import com.example.scorebroadcaster.data.entity.DismissalType
import com.example.scorebroadcaster.data.entity.ExtrasBreakdown
import com.example.scorebroadcaster.data.entity.Match
import com.example.scorebroadcaster.data.entity.Player
import com.example.scorebroadcaster.data.entity.Team
import com.example.scorebroadcaster.domain.BallEvent
import com.example.scorebroadcaster.viewmodel.MatchViewModel

/** Identifies which type of extra delivery the scorer is entering. */
enum class ExtraType(val label: String) {
    WIDE("Wide"),
    NO_BALL("No Ball"),
    BYE("Bye"),
    LEG_BYE("Leg Bye"),
}

@Composable
fun ScoringScreen(
    matchViewModel: MatchViewModel = viewModel(),
    onMatchDetails: () -> Unit = {},
    onViewScorecard: () -> Unit = {},
    onCameraPreview: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by matchViewModel.state.collectAsState()
    val console by matchViewModel.consoleState.collectAsState()
    val match by matchViewModel.activeMatch.collectAsState()
    // Capture a non-nullable snapshot so inner lambdas and blocks can smart-cast.
    val activeMatch: Match? = match

    // Show openers-setup dialog when the innings phase is SETUP
    var setupDialogVisible by remember { mutableStateOf(false) }
    LaunchedEffect(console.phase) {
        if (console.phase == InningsPhase.SETUP && activeMatch != null) {
            setupDialogVisible = true
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- Quick navigation bar ---
            if (activeMatch != null) {
                QuickNavBar(
                    onMatchDetails = onMatchDetails,
                    onViewScorecard = onViewScorecard,
                    onCameraPreview = onCameraPreview
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // --- Match header ---
            if (activeMatch != null) {
                MatchHeaderSection(match = activeMatch, console = console)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // --- Score display ---
            ScoreDisplaySection(state = state)
            Spacer(modifier = Modifier.height(8.dp))

            // --- Chase info (2nd innings only) ---
            if (console.phase == InningsPhase.SECOND_INNINGS && activeMatch != null) {
                ChaseInfoSection(
                    runsScored = state.runs,
                    target = console.target,
                    overs = state.overs,
                    balls = state.balls,
                    oversLimit = activeMatch.overs
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // --- Innings break ---
            if (console.phase == InningsPhase.INNINGS_BREAK && activeMatch != null) {
                InningsBreakSection(
                    battingFirstTeam = activeMatch.battingFirst.name,
                    firstInningsRuns = console.firstInningsRuns,
                    firstInningsWickets = console.firstInningsWickets,
                    target = console.target,
                    onStartSecondInnings = { matchViewModel.startSecondInnings() },
                    onViewScorecard = onViewScorecard
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // --- Last 6 balls ---
            LastBallsRow(lastBalls = state.lastBalls)
            Spacer(modifier = Modifier.height(12.dp))

            // --- Current players card ---
            if (console.phase == InningsPhase.FIRST_INNINGS ||
                console.phase == InningsPhase.SECOND_INNINGS
            ) {
                PlayersSection(console = console)
                Spacer(modifier = Modifier.height(12.dp))
            }

            // --- Innings setup required banner ---
            if (console.phase == InningsPhase.SETUP && !setupDialogVisible && activeMatch != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
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
                        Text(
                            text = "Innings setup required before scoring can begin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { setupDialogVisible = true }) {
                            Text(
                                "Setup",
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // --- Scoring buttons ---
            val scoringEnabled = (console.phase == InningsPhase.FIRST_INNINGS ||
                    console.phase == InningsPhase.SECOND_INNINGS) &&
                    console.pendingAction == null
            // Wicket details dialog state — shown before dispatching the Wicket event
            var showWicketDialog by remember { mutableStateOf(false) }
            // Extras entry dialog state
            var extrasDialogType by remember { mutableStateOf<ExtraType?>(null) }
            ScoringButtonsSection(
                onEvent = { matchViewModel.addEvent(it) },
                onUndo = { matchViewModel.undo() },
                onWicket = { showWicketDialog = true },
                onExtras = { type -> extrasDialogType = type },
                enabled = scoringEnabled
            )
            if (showWicketDialog) {
                val bowlingTeamPlayers = when {
                    activeMatch == null -> emptyList()
                    console.inningsNumber == 1 -> activeMatch.bowlingFirst.players
                    else -> activeMatch.battingFirst.players
                }
                WicketDetailsDialog(
                    striker = console.striker,
                    nonStriker = console.nonStriker,
                    bowlingTeamPlayers = bowlingTeamPlayers,
                    currentBowler = console.currentBowler,
                    onConfirm = { dismissal ->
                        showWicketDialog = false
                        matchViewModel.addEvent(ScoreEvent.Wicket(dismissal))
                    },
                    onDismiss = { showWicketDialog = false }
                )
            }
            val currentExtrasType = extrasDialogType
            if (currentExtrasType != null) {
                val bowlingTeamPlayers = when {
                    activeMatch == null -> emptyList()
                    console.inningsNumber == 1 -> activeMatch.bowlingFirst.players
                    else -> activeMatch.battingFirst.players
                }
                ExtrasEntryDialog(
                    initialType = currentExtrasType,
                    striker = console.striker,
                    nonStriker = console.nonStriker,
                    bowlingTeamPlayers = bowlingTeamPlayers,
                    onConfirm = { ballEvent ->
                        extrasDialogType = null
                        matchViewModel.addBallEvent(ballEvent)
                    },
                    onDismiss = { extrasDialogType = null }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // --- Innings / match control ---
            if (activeMatch != null) {
                InningsControlSection(
                    console = console,
                    onEndFirstInnings = { matchViewModel.endFirstInnings() },
                    onEndMatch = { matchViewModel.endMatch() }
                )
            }

            // --- Add player during match ---
            var showAddPlayerDialog by remember { mutableStateOf(false) }
            if (activeMatch != null &&
                (console.phase == InningsPhase.FIRST_INNINGS ||
                        console.phase == InningsPhase.SECOND_INNINGS)
            ) {
                TextButton(onClick = { showAddPlayerDialog = true }) {
                    Text(
                        "＋ Add player to team",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (showAddPlayerDialog) {
                    AddPlayerToMatchDialog(
                        battingTeamName = console.battingTeamName,
                        bowlingTeamName = console.bowlingTeamName,
                        onDismiss = { showAddPlayerDialog = false },
                        onConfirm = { name, toBatting ->
                            matchViewModel.addPlayerToTeam(Player(name = name), toBatting)
                            showAddPlayerDialog = false
                        }
                    )
                }
            }

            // --- Match complete banner ---
            if (console.phase == InningsPhase.MATCH_COMPLETE) {
                Spacer(modifier = Modifier.height(16.dp))
                MatchCompleteSection(
                    runsScored = state.runs,
                    wickets = state.wickets,
                    console = console,
                    battingTeamName = state.teamAName,
                    bowlingTeamName = state.teamBName
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onViewScorecard) { Text("View Scorecard") }
                    OutlinedButton(onClick = onMatchDetails) { Text("Match Hub") }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Extras (always visible during play) ---
            if (console.phase == InningsPhase.FIRST_INNINGS ||
                console.phase == InningsPhase.SECOND_INNINGS
            ) {
                Text(
                    text = "Extras: ${state.extras}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }

        // --- Pending action dialogs (rendered on top of everything) ---
        when (val action = console.pendingAction) {
            is PendingAction.SelectNextBatter -> {
                val title = if (action.replacingStriker) "Select Next Batter" else "Select Next Non-Striker"
                Log.d("WicketFlow", "Next batter dialog shown (${action.availablePlayers.size} players available, replacingStriker=${action.replacingStriker})")
                SelectPlayerDialog(
                    title = title,
                    players = action.availablePlayers,
                    onPlayerSelected = { matchViewModel.selectNextBatter(it) },
                    onAddNewPlayer = { name ->
                        val newPlayer = Player(name = name)
                        matchViewModel.addPlayerToTeam(newPlayer, addToBattingTeam = true)
                        matchViewModel.selectNextBatter(newPlayer)
                    },
                    onAllOut = { matchViewModel.endInningsAsAllOut() }
                )
            }
            is PendingAction.SelectBowler -> SelectPlayerDialog(
                title = "Select Bowler",
                players = action.availablePlayers,
                onPlayerSelected = { matchViewModel.changeBowler(it) },
                onAddNewPlayer = { name ->
                    val newPlayer = Player(name = name)
                    matchViewModel.addPlayerToTeam(newPlayer, addToBattingTeam = false)
                    matchViewModel.changeBowler(newPlayer)
                }
            )
            null -> Unit
        }

        // --- Openers setup dialog ---
        if (setupDialogVisible && activeMatch != null && console.phase == InningsPhase.SETUP) {
            val battingTeam = if (console.inningsNumber == 1) activeMatch.battingFirst
                              else activeMatch.bowlingFirst
            val bowlingTeam = if (console.inningsNumber == 1) activeMatch.bowlingFirst
                              else activeMatch.battingFirst
            SetupOpenersDialog(
                inningsNumber = console.inningsNumber,
                battingTeam = battingTeam,
                bowlingTeam = bowlingTeam,
                onConfirm = { striker, nonStriker, bowler ->
                    matchViewModel.setOpeners(striker, nonStriker, bowler)
                    setupDialogVisible = false
                },
                onDismiss = { setupDialogVisible = false },
                onAddPlayerToBattingTeam = { name ->
                    matchViewModel.addPlayerToTeam(Player(name = name), addToBattingTeam = true)
                },
                onAddPlayerToBowlingTeam = { name ->
                    matchViewModel.addPlayerToTeam(Player(name = name), addToBattingTeam = false)
                }
            )
        }
    }
}

// =============================================================================
// Match header
// =============================================================================

@Composable
private fun MatchHeaderSection(match: Match, console: ScoringConsoleState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = match.displayTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${match.format.label.substringBefore(" (")} · ${match.overs} overs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.extraSmall
            ) {
                val inningsSuffix = if (console.inningsNumber == 1) "st" else "nd"
                Text(
                    text = "${console.inningsNumber}$inningsSuffix Innings",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "Bat: ${console.battingTeamName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Bowl: ${console.bowlingTeamName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

// =============================================================================
// Score display
// =============================================================================

@Composable
private fun ScoreDisplaySection(
    state: MatchState
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = state.teamAName,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = "${state.runs}/${state.wickets}",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Overs: ${state.overs}.${state.balls}",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

// =============================================================================
// Chase / target info
// =============================================================================

@Composable
private fun ChaseInfoSection(
    runsScored: Int,
    target: Int,
    overs: Int,
    balls: Int,
    oversLimit: Int
) {
    val runsNeeded = (target - runsScored).coerceAtLeast(0)
    val ballsBowled = overs * 6 + balls
    val totalBalls = oversLimit * 6
    val ballsRemaining = (totalBalls - ballsBowled).coerceAtLeast(0)

    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            ChaseInfoItem(label = "Target", value = "$target")
            ChaseInfoItem(
                label = "Need",
                value = if (runsScored >= target) "Won!" else "$runsNeeded"
            )
            ChaseInfoItem(label = "Balls left", value = "$ballsRemaining")
        }
    }
}

@Composable
private fun ChaseInfoItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
        )
    }
}

// =============================================================================
// Last balls row
// =============================================================================

@Composable
private fun LastBallsRow(lastBalls: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        lastBalls.forEach { ball ->
            val bgColor = when {
                ball == "W" -> MaterialTheme.colorScheme.error
                ball.startsWith("Wd") || ball.startsWith("NB") -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.primary
            }
            Surface(color = bgColor, shape = MaterialTheme.shapes.small) {
                Text(
                    text = ball,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

// =============================================================================
// Current players card
// =============================================================================

@Composable
private fun PlayersSection(console: ScoringConsoleState) {
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "At the Crease",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )

            // Striker
            console.strikerEntry?.let { BatterRow(entry = it, isStriker = true) }
                ?: console.striker?.let {
                    Text("${it.name} *", style = MaterialTheme.typography.bodySmall)
                }

            // Non-striker
            console.nonStrikerEntry?.let { BatterRow(entry = it, isStriker = false) }
                ?: console.nonStriker?.let {
                    Text(it.name, style = MaterialTheme.typography.bodySmall)
                }

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            // Bowler
            console.currentBowlerEntry?.let { BowlerRow(entry = it) }
                ?: console.currentBowler?.let {
                    Text("⚾ ${it.name}", style = MaterialTheme.typography.bodySmall)
                }
        }
    }
}

@Composable
private fun BatterRow(entry: BattingEntry, isStriker: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${entry.player.name}${if (isStriker) " *" else ""}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isStriker) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${entry.runs} (${entry.balls})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        if (entry.fours > 0 || entry.sixes > 0) {
            Text(
                text = "  4s:${entry.fours} 6s:${entry.sixes}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun BowlerRow(entry: BowlingEntry) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "⚾ ${entry.player.name}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${entry.overs}.${entry.balls}  ${entry.runs} runs  ${entry.wickets}w",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

// =============================================================================
// Scoring buttons
// =============================================================================

@Composable
private fun ScoringButtonsSection(
    onEvent: (ScoreEvent) -> Unit,
    onUndo: () -> Unit,
    onWicket: () -> Unit,
    onExtras: (ExtraType) -> Unit,
    enabled: Boolean
) {
    // Run buttons: 0 1 2 3 4 6
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(0, 1, 2, 3, 4, 6).forEach { runs ->
            Button(onClick = { onEvent(ScoreEvent.Run(runs)) }, enabled = enabled) {
                Text("$runs")
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))

    // Wicket / Extras row
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(
            onClick = onWicket,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) { Text("W") }
        ExtraType.entries.forEach { type ->
            Button(onClick = { onExtras(type) }, enabled = enabled) { Text(type.label) }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))

    OutlinedButton(
        onClick = onUndo,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
        )
    ) {
        Text("Undo")
    }
}

// =============================================================================
// Innings / match controls
// =============================================================================

@Composable
private fun InningsControlSection(
    console: ScoringConsoleState,
    onEndFirstInnings: () -> Unit,
    onEndMatch: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (console.phase == InningsPhase.FIRST_INNINGS) {
            OutlinedButton(onClick = onEndFirstInnings) {
                Text("End 1st Innings")
            }
        }
        if (console.phase == InningsPhase.SECOND_INNINGS) {
            OutlinedButton(
                onClick = onEndMatch,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("End Match")
            }
        }
    }
}

// =============================================================================
// Match complete
// =============================================================================

@Composable
private fun MatchCompleteSection(
    runsScored: Int,
    wickets: Int,
    console: ScoringConsoleState,
    battingTeamName: String,
    bowlingTeamName: String
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Match Complete",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (console.inningsNumber == 2) {
                val runsNeeded = console.target - runsScored
                val result = when {
                    runsScored >= console.target ->
                        "$battingTeamName won by ${10 - wickets} wickets!"
                    wickets >= 10 ->
                        // target = firstInningsRuns + 1, so margin = target - 1 - runsScored = runsNeeded - 1
                        "$bowlingTeamName won by ${runsNeeded - 1} runs!"
                    else -> "Match ended"
                }
                Text(result, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    text = "1st innings: ${console.firstInningsRuns}/${console.firstInningsWickets}" +
                            "   |   2nd innings: $runsScored/$wickets",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// =============================================================================
// Player-selection dialog (wicket / bowler change)
// =============================================================================

/**
 * Non-dismissible player list dialog.
 *
 * @param onAddNewPlayer Optional callback: when non-null, an "Add new player" inline field
 *   is shown so the scorer can create a player on the fly without closing this dialog.
 *   The callback receives the trimmed player name.
 * @param onAllOut Optional callback: when non-null, a "No more players / All out" button is
 *   shown so the scorer can end the innings immediately without selecting a batter.
 */
@Composable
private fun SelectPlayerDialog(
    title: String,
    players: List<Player>,
    onPlayerSelected: (Player) -> Unit,
    onAddNewPlayer: ((String) -> Unit)? = null,
    onAllOut: (() -> Unit)? = null
) {
    var newPlayerName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { /* must select */ },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (players.isEmpty() && onAddNewPlayer == null) {
                    Text("No players available.")
                }
                players.forEach { player ->
                    TextButton(
                        onClick = { onPlayerSelected(player) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = player.name,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (onAddNewPlayer != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "Add new player",
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
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val name = newPlayerName.trim()
                                if (name.isNotEmpty()) {
                                    onAddNewPlayer(name)
                                    newPlayerName = ""
                                }
                            },
                            enabled = newPlayerName.isNotBlank()
                        ) { Text("Add") }
                    }
                }
                if (onAllOut != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Button(
                        onClick = onAllOut,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("No more players / All out")
                    }
                }
            }
        },
        confirmButton = {}
    )
}

// =============================================================================
// Wicket details dialog
// =============================================================================

/**
 * Dialog shown when the scorer taps the W (Wicket) button.
 *
 * Lets the scorer specify:
 * - Who got out (striker or non-striker)
 * - How they were dismissed (dismissal type)
 * - Optional fielder involved (catcher, wicketkeeper, or run-out fielder)
 */
@Composable
internal fun WicketDetailsDialog(
    striker: Player?,
    nonStriker: Player?,
    bowlingTeamPlayers: List<Player>,
    currentBowler: Player?,
    onConfirm: (DismissalDetail) -> Unit,
    onDismiss: () -> Unit
) {
    // Default to striker out (the most common case)
    var batterOut by remember { mutableStateOf(striker ?: nonStriker) }
    var selectedType by remember { mutableStateOf(DismissalType.BOWLED) }
    var selectedFielder by remember { mutableStateOf<Player?>(null) }

    // Fielder is relevant for Caught, Stumped, and Run Out dismissals.
    val requiresFielder = selectedType in listOf(
        DismissalType.CAUGHT, DismissalType.STUMPED, DismissalType.RUN_OUT
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wicket Details") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // --- Who got out ---
                Text("Who got out?", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (striker != null) {
                        FilterChip(
                            selected = batterOut?.id == striker.id,
                            onClick = { batterOut = striker },
                            label = { Text("${striker.name} (striker)") }
                        )
                    }
                    if (nonStriker != null) {
                        FilterChip(
                            selected = batterOut?.id == nonStriker.id,
                            onClick = { batterOut = nonStriker },
                            label = { Text("${nonStriker.name} (non-striker)") }
                        )
                    }
                }

                HorizontalDivider()

                // --- Dismissal type ---
                Text("How?", style = MaterialTheme.typography.labelMedium)
                // Two rows of chips for the 6 dismissal types
                val types = DismissalType.entries
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        types.take(3).forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = {
                                    selectedType = type
                                    selectedFielder = null
                                },
                                label = { Text(type.label) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        types.drop(3).forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = {
                                    selectedType = type
                                    selectedFielder = null
                                },
                                label = { Text(type.label) }
                            )
                        }
                    }
                }

                // --- Fielder selection (for Caught / Stumped / Run Out) ---
                if (requiresFielder) {
                    HorizontalDivider()
                    val fielderLabel = when (selectedType) {
                        DismissalType.CAUGHT -> "Catcher"
                        DismissalType.STUMPED -> "Wicketkeeper"
                        DismissalType.RUN_OUT -> "Fielder (optional)"
                        else -> "Fielder"
                    }
                    Text(fielderLabel, style = MaterialTheme.typography.labelMedium)
                    if (bowlingTeamPlayers.isEmpty()) {
                        Text(
                            "No fielding team players registered.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    } else {
                        bowlingTeamPlayers.forEach { player ->
                            FilterChip(
                                selected = selectedFielder?.id == player.id,
                                onClick = {
                                    selectedFielder = if (selectedFielder?.id == player.id) null else player
                                },
                                label = { Text(player.name) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val out = batterOut ?: return@Button
                    onConfirm(
                        DismissalDetail(
                            batter = out,
                            dismissalType = selectedType,
                            fielder = if (requiresFielder) selectedFielder else null,
                            bowler = currentBowler
                        )
                    )
                },
                enabled = batterOut != null
            ) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// =============================================================================
// Extras entry dialog
// =============================================================================

/**
 * Dialog shown when the scorer taps one of the extras buttons (Wide, No Ball, Bye, Leg Bye).
 *
 * Lets the scorer specify:
 * - Extra type (pre-filled from the button that was tapped, but changeable)
 * - Total runs on the delivery (defaults to 1 for all types)
 * - Optional run-out wicket on the same delivery
 *   - Which batter was run out (striker or non-striker)
 *   - Optional fielder involved
 *
 * Only Run Out is allowed as a wicket mode for extras, matching real-world cricket rules.
 */
@Composable
internal fun ExtrasEntryDialog(
    initialType: ExtraType,
    striker: Player?,
    nonStriker: Player?,
    bowlingTeamPlayers: List<Player>,
    onConfirm: (BallEvent) -> Unit,
    onDismiss: () -> Unit
) {
    var extraType by remember { mutableStateOf(initialType) }
    var selectedRuns by remember { mutableStateOf(1) }
    var customRunsText by remember { mutableStateOf("") }
    var useCustomRuns by remember { mutableStateOf(false) }
    var hasWicket by remember { mutableStateOf(false) }
    var batterOut by remember { mutableStateOf(striker ?: nonStriker) }
    var selectedFielder by remember { mutableStateOf<Player?>(null) }

    // Reset wicket state whenever the extra type changes
    LaunchedEffect(extraType) {
        hasWicket = false
        batterOut = striker ?: nonStriker
        selectedFielder = null
    }

    val totalRuns = if (useCustomRuns) customRunsText.toIntOrNull()?.coerceAtLeast(1) ?: selectedRuns else selectedRuns

    val isValid = !hasWicket || batterOut != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Extras Entry") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // --- Extra type selector ---
                Text("Extra type", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExtraType.entries.forEach { type ->
                        FilterChip(
                            selected = extraType == type,
                            onClick = { extraType = type },
                            label = { Text(type.label) }
                        )
                    }
                }

                HorizontalDivider()

                // --- Runs selector ---
                Text("Runs on delivery", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 2, 3, 4).forEach { runs ->
                        FilterChip(
                            selected = !useCustomRuns && selectedRuns == runs,
                            onClick = { selectedRuns = runs; useCustomRuns = false },
                            label = { Text("$runs") }
                        )
                    }
                    FilterChip(
                        selected = useCustomRuns,
                        onClick = { useCustomRuns = true },
                        label = { Text("5+") }
                    )
                }
                if (useCustomRuns) {
                    OutlinedTextField(
                        value = customRunsText,
                        onValueChange = { customRunsText = it.filter { char -> char.isDigit() } },
                        label = { Text("Enter runs") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider()

                // --- Wicket toggle ---
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = hasWicket,
                        onCheckedChange = { checked ->
                            hasWicket = checked
                            if (!checked) { batterOut = striker ?: nonStriker; selectedFielder = null }
                        }
                    )
                    Text("Wicket on this ball (Run Out only)", style = MaterialTheme.typography.bodyMedium)
                }

                // --- Wicket detail section (only when hasWicket is true) ---
                if (hasWicket) {
                    HorizontalDivider()
                    Text("Who was run out?", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (striker != null) {
                            FilterChip(
                                selected = batterOut?.id == striker.id,
                                onClick = { batterOut = striker },
                                label = { Text("${striker.name} (striker)") }
                            )
                        }
                        if (nonStriker != null) {
                            FilterChip(
                                selected = batterOut?.id == nonStriker.id,
                                onClick = { batterOut = nonStriker },
                                label = { Text("${nonStriker.name} (non-striker)") }
                            )
                        }
                    }

                    Text("Fielder (optional)", style = MaterialTheme.typography.labelMedium)
                    if (bowlingTeamPlayers.isEmpty()) {
                        Text(
                            "No fielding team players registered.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            bowlingTeamPlayers.forEach { player ->
                                FilterChip(
                                    selected = selectedFielder?.id == player.id,
                                    onClick = {
                                        selectedFielder = if (selectedFielder?.id == player.id) null else player
                                    },
                                    label = { Text(player.name) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val ballEvent = buildExtrasEvent(extraType, totalRuns, hasWicket, batterOut, selectedFielder)
                    onConfirm(ballEvent)
                },
                enabled = isValid
            ) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Constructs a [BallEvent] for an extras delivery.
 *
 * Run mapping:
 * - **Wide**: all runs go to wides (including the 1-run penalty). User sees "total runs".
 * - **No Ball**: 1 run is the no-ball penalty (extras); remaining runs are credited off bat.
 * - **Bye / Leg Bye**: all runs go directly to byes / leg-byes.
 *
 * A wicket on an extras delivery is always a **Run Out** and does NOT credit the bowler.
 */
private fun buildExtrasEvent(
    type: ExtraType,
    runs: Int,
    hasWicket: Boolean,
    batterOut: Player?,
    fielder: Player?
): BallEvent {
    val dismissal: DismissalDetail? = if (hasWicket && batterOut != null) {
        DismissalDetail(
            batter = batterOut,
            dismissalType = DismissalType.RUN_OUT,
            fielder = fielder,
            bowler = null
        )
    } else null

    return when (type) {
        ExtraType.WIDE -> BallEvent(
            extras = ExtrasBreakdown(wides = runs),
            wicket = hasWicket,
            dismissalDetail = dismissal,
            countsAsBall = false
        )
        ExtraType.NO_BALL -> BallEvent(
            runsOffBat = maxOf(0, runs - 1),
            extras = ExtrasBreakdown(noBalls = 1),
            wicket = hasWicket,
            dismissalDetail = dismissal,
            countsAsBall = false
        )
        ExtraType.BYE -> BallEvent(
            extras = ExtrasBreakdown(byes = runs),
            wicket = hasWicket,
            dismissalDetail = dismissal,
            countsAsBall = true
        )
        ExtraType.LEG_BYE -> BallEvent(
            extras = ExtrasBreakdown(legByes = runs),
            wicket = hasWicket,
            dismissalDetail = dismissal,
            countsAsBall = true
        )
    }
}

// =============================================================================
// Add player during an active match
// =============================================================================

@Composable
private fun AddPlayerToMatchDialog(
    battingTeamName: String,
    bowlingTeamName: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, addToBattingTeam: Boolean) -> Unit
) {
    var playerName by remember { mutableStateOf("") }
    var addToBatting by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Player") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = playerName,
                    onValueChange = { playerName = it },
                    label = { Text("Player name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Add to", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = addToBatting,
                        onClick = { addToBatting = true },
                        label = { Text(battingTeamName) }
                    )
                    FilterChip(
                        selected = !addToBatting,
                        onClick = { addToBatting = false },
                        label = { Text(bowlingTeamName) }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(playerName.trim(), addToBatting) },
                enabled = playerName.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// =============================================================================
// Opening batters + bowler setup dialog
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupOpenersDialog(
    inningsNumber: Int,
    battingTeam: Team,
    bowlingTeam: Team,
    onConfirm: (striker: Player, nonStriker: Player, bowler: Player) -> Unit,
    onDismiss: () -> Unit,
    onAddPlayerToBattingTeam: (name: String) -> Unit,
    onAddPlayerToBowlingTeam: (name: String) -> Unit
) {
    var striker by remember { mutableStateOf<Player?>(null) }
    var nonStriker by remember { mutableStateOf<Player?>(null) }
    var bowler by remember { mutableStateOf<Player?>(null) }
    var newBatterName by remember { mutableStateOf("") }
    var newBowlerName by remember { mutableStateOf("") }

    val inningsSuffix = if (inningsNumber == 1) "st" else "nd"
    val needsMoreBatters = battingTeam.players.size < 2
    val needsMoreBowlers = bowlingTeam.players.isEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$inningsNumber$inningsSuffix Innings Setup") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Batting: ${battingTeam.name}",
                    style = MaterialTheme.typography.labelMedium
                )
                if (needsMoreBatters) {
                    Text(
                        text = "You need at least 2 batters to start the innings.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                PlayerDropdown(
                    label = "Striker",
                    players = battingTeam.players.filter { it.id != nonStriker?.id },
                    selected = striker,
                    onSelected = { striker = it }
                )
                PlayerDropdown(
                    label = "Non-striker",
                    players = battingTeam.players.filter { it.id != striker?.id },
                    selected = nonStriker,
                    onSelected = { nonStriker = it }
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newBatterName,
                        onValueChange = { newBatterName = it },
                        label = { Text("Add batter") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val name = newBatterName.trim()
                            if (name.isNotBlank()) {
                                onAddPlayerToBattingTeam(name)
                                newBatterName = ""
                            }
                        },
                        enabled = newBatterName.isNotBlank()
                    ) { Text("Add") }
                }
                HorizontalDivider()
                Text(
                    text = "Bowling: ${bowlingTeam.name}",
                    style = MaterialTheme.typography.labelMedium
                )
                if (needsMoreBowlers) {
                    Text(
                        text = "You need at least 1 bowler to start the innings.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                PlayerDropdown(
                    label = "Opening bowler",
                    players = bowlingTeam.players,
                    selected = bowler,
                    onSelected = { bowler = it }
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newBowlerName,
                        onValueChange = { newBowlerName = it },
                        label = { Text("Add bowler") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val name = newBowlerName.trim()
                            if (name.isNotBlank()) {
                                onAddPlayerToBowlingTeam(name)
                                newBowlerName = ""
                            }
                        },
                        enabled = newBowlerName.isNotBlank()
                    ) { Text("Add") }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val s = striker; val ns = nonStriker; val b = bowler
                    if (s != null && ns != null && b != null) onConfirm(s, ns, b)
                },
                enabled = striker != null && nonStriker != null && bowler != null
            ) {
                Text("Start")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Later") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerDropdown(
    label: String,
    players: List<Player>,
    selected: Player?,
    onSelected: (Player) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.name ?: "— select —",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            players.forEach { player ->
                DropdownMenuItem(
                    text = { Text(player.name) },
                    onClick = { onSelected(player); expanded = false }
                )
            }
        }
    }
}

// =============================================================================
// Quick navigation bar
// =============================================================================

@Composable
private fun QuickNavBar(
    onMatchDetails: () -> Unit,
    onViewScorecard: () -> Unit,
    onCameraPreview: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onMatchDetails,
            modifier = Modifier.weight(1f)
        ) { Text("Match Hub", style = MaterialTheme.typography.labelSmall) }
        OutlinedButton(
            onClick = onViewScorecard,
            modifier = Modifier.weight(1f)
        ) { Text("Scorecard", style = MaterialTheme.typography.labelSmall) }
        OutlinedButton(
            onClick = onCameraPreview,
            modifier = Modifier.weight(1f)
        ) { Text("Camera", style = MaterialTheme.typography.labelSmall) }
    }
}

// =============================================================================
// Innings break section
// =============================================================================

@Composable
private fun InningsBreakSection(
    battingFirstTeam: String,
    firstInningsRuns: Int,
    firstInningsWickets: Int,
    target: Int,
    onStartSecondInnings: () -> Unit,
    onViewScorecard: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Innings Break",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$battingFirstTeam: $firstInningsRuns/$firstInningsWickets",
                style = MaterialTheme.typography.bodyLarge
            )
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "Target: $target",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStartSecondInnings) {
                    Text("Start 2nd Innings")
                }
                OutlinedButton(onClick = onViewScorecard) {
                    Text("Scorecard")
                }
            }
        }
    }
}
