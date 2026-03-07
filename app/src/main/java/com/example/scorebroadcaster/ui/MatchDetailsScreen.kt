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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.scorebroadcaster.data.InningsPhase
import com.example.scorebroadcaster.data.MatchState
import com.example.scorebroadcaster.data.ScoringConsoleState
import com.example.scorebroadcaster.data.entity.Match
import com.example.scorebroadcaster.data.entity.MatchStatus
import com.example.scorebroadcaster.data.entity.MatchVisibility
import com.example.scorebroadcaster.data.entity.Player
import com.example.scorebroadcaster.viewmodel.MatchSessionViewModel
import com.example.scorebroadcaster.viewmodel.MatchViewModel

@Composable
fun MatchDetailsScreen(
    matchSessionViewModel: MatchSessionViewModel,
    matchViewModel: MatchViewModel,
    onStartScoring: () -> Unit,
    onCameraPreview: () -> Unit,
    onGoLive: () -> Unit,
    onViewScorecard: () -> Unit,
    onViewTimeline: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeMatch by matchSessionViewModel.activeMatch.collectAsState()
    val scoringMatch by matchViewModel.activeMatch.collectAsState()
    val state by matchViewModel.state.collectAsState()
    val console by matchViewModel.consoleState.collectAsState()

    val match = activeMatch ?: run {
        Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("No match selected.", style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    val isThisMatch = scoringMatch?.id == match.id

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- Match title & status ---
            MatchDetailHeader(
                match = match,
                console = if (isThisMatch) console else null
            )

            HorizontalDivider()

            // --- Match info rows ---
            MatchInfoGrid(match = match)

            // --- Score summary (if scoring has started) ---
            if (isThisMatch && console.phase != InningsPhase.SETUP) {
                HorizontalDivider()
                ScoreSummarySection(
                    match = match,
                    state = state,
                    console = console
                )
            }

            HorizontalDivider()

            // --- Action buttons ---
            var showAddPlayerDialog by remember { mutableStateOf(false) }
            MatchActionButtons(
                match = match,
                console = if (isThisMatch) console else null,
                onStartScoring = onStartScoring,
                onCameraPreview = onCameraPreview,
                onGoLive = onGoLive,
                onViewScorecard = onViewScorecard,
                onViewTimeline = onViewTimeline,
                onAddPlayer = if (isThisMatch) { { showAddPlayerDialog = true } } else null
            )

            if (showAddPlayerDialog && isThisMatch) {
                val battingTeamName = console.battingTeamName
                val bowlingTeamName = console.bowlingTeamName
                AddPlayerToTeamDialog(
                    battingTeamName = battingTeamName,
                    bowlingTeamName = bowlingTeamName,
                    onDismiss = { showAddPlayerDialog = false },
                    onConfirm = { name, toBatting ->
                        matchViewModel.addPlayerToTeam(Player(name = name), toBatting)
                        showAddPlayerDialog = false
                    }
                )
            }

            HorizontalDivider()

            // --- Publishing controls (placeholder — backend not yet implemented) ---
            MatchPublishingSection(match = match)

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// =============================================================================
// Header with match title and status badge
// =============================================================================

@Composable
private fun MatchDetailHeader(match: Match, console: ScoringConsoleState?) {
    val statusLabel = deriveStatusLabel(match, console)
    val statusColor = when {
        console?.phase == InningsPhase.FIRST_INNINGS ||
                console?.phase == InningsPhase.SECOND_INNINGS -> MaterialTheme.colorScheme.error
        console?.phase == InningsPhase.MATCH_COMPLETE ||
                match.status == MatchStatus.COMPLETED -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.primary
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = match.displayTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Surface(
            color = statusColor.copy(alpha = 0.12f),
            shape = MaterialTheme.shapes.extraSmall
        ) {
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelMedium,
                color = statusColor,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

private fun deriveStatusLabel(match: Match, console: ScoringConsoleState?): String {
    if (console == null) return match.status.label
    return when (console.phase) {
        InningsPhase.SETUP -> "Setting Up"
        InningsPhase.FIRST_INNINGS -> "● Live – 1st Innings"
        InningsPhase.INNINGS_BREAK -> "Innings Break"
        InningsPhase.SECOND_INNINGS -> "● Live – 2nd Innings"
        InningsPhase.MATCH_COMPLETE -> "Completed"
    }
}

// =============================================================================
// Match info grid (format, toss, batting/bowling first)
// =============================================================================

@Composable
private fun MatchInfoGrid(match: Match) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MatchInfoRow("Format", "${match.format.label.substringBefore(" (")} · ${match.overs} overs")
        MatchInfoRow("Toss", match.tossResultText)
        MatchInfoRow("Bat 1st", match.battingFirst.name)
        MatchInfoRow("Bowl 1st", match.bowlingFirst.name)
    }
}

@Composable
private fun MatchInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.weight(0.35f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.65f)
        )
    }
}

// =============================================================================
// Score summary section
// =============================================================================

@Composable
private fun ScoreSummarySection(
    match: Match,
    state: MatchState,
    console: ScoringConsoleState
) {
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Score Summary",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )

            when (console.phase) {
                InningsPhase.FIRST_INNINGS -> {
                    ScoreLine(
                        team = console.battingTeamName,
                        score = "${state.runs}/${state.wickets}",
                        detail = "${state.overs}.${state.balls} overs"
                    )
                }
                InningsPhase.INNINGS_BREAK -> {
                    ScoreLine(
                        team = match.battingFirst.name,
                        score = "${console.firstInningsRuns}/${console.firstInningsWickets}",
                        detail = "1st Innings"
                    )
                    Text(
                        text = "Target: ${console.target}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                InningsPhase.SECOND_INNINGS -> {
                    ScoreLine(
                        team = match.battingFirst.name,
                        score = "${console.firstInningsRuns}/${console.firstInningsWickets}",
                        detail = "1st Innings"
                    )
                    ScoreLine(
                        team = console.battingTeamName,
                        score = "${state.runs}/${state.wickets}",
                        detail = "${state.overs}.${state.balls} overs  •  need ${(console.target - state.runs).coerceAtLeast(0)} more"
                    )
                }
                InningsPhase.MATCH_COMPLETE -> {
                    ScoreLine(
                        team = match.battingFirst.name,
                        score = "${console.firstInningsRuns}/${console.firstInningsWickets}",
                        detail = "1st Innings"
                    )
                    ScoreLine(
                        team = match.bowlingFirst.name,
                        score = "${state.runs}/${state.wickets}",
                        detail = "2nd Innings"
                    )
                    val resultText = buildResultText(state, console)
                    if (resultText.isNotEmpty()) {
                        Text(
                            text = resultText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun ScoreLine(team: String, score: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = team,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(text = score, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

private fun buildResultText(state: MatchState, console: ScoringConsoleState): String {
    if (console.inningsNumber != 2) return ""
    val runsNeeded = console.target - state.runs
    return when {
        state.runs >= console.target ->
            "${state.teamAName} won by ${10 - state.wickets} wickets"
        state.wickets >= 10 ->
            "${state.teamBName} won by ${(runsNeeded - 1).coerceAtLeast(0)} runs"
        else -> "Match ended"
    }
}

// =============================================================================
// Action buttons
// =============================================================================

@Composable
private fun MatchActionButtons(
    match: Match,
    console: ScoringConsoleState?,
    onStartScoring: () -> Unit,
    onCameraPreview: () -> Unit,
    onGoLive: () -> Unit,
    onViewScorecard: () -> Unit,
    onViewTimeline: () -> Unit,
    onAddPlayer: (() -> Unit)? = null
) {
    val isComplete = console?.phase == InningsPhase.MATCH_COMPLETE ||
            match.status == MatchStatus.COMPLETED

    val scoringLabel = when {
        isComplete -> "View Scoring Console"
        console == null || console.phase == InningsPhase.SETUP -> "Start Scoring"
        else -> "Resume Scoring"
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onStartScoring,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(scoringLabel, style = MaterialTheme.typography.titleSmall)
        }

        OutlinedButton(
            onClick = onViewScorecard,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("View Scorecard", style = MaterialTheme.typography.titleSmall)
        }

        OutlinedButton(
            onClick = onViewTimeline,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Ball Timeline", style = MaterialTheme.typography.titleSmall)
        }

        if (onAddPlayer != null && !isComplete) {
            OutlinedButton(
                onClick = onAddPlayer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("＋ Add Player to Team", style = MaterialTheme.typography.titleSmall)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onCameraPreview,
                modifier = Modifier.weight(1f)
            ) {
                Text("Camera Preview")
            }
            Button(
                onClick = onGoLive,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Text("Go Live")
            }
        }
    }
}

// =============================================================================
// Publishing controls (placeholder — backend not yet implemented)
// =============================================================================

/**
 * Placeholder section for match publishing controls.
 *
 * All actions here are disabled today.  They are present to make the future
 * one-scorer / many-viewers product direction visible in the UI and to reserve
 * the correct layout slot for when backend publishing is added.
 *
 * Future implementation notes:
 * - "Publish Match" will set [Match.visibility] to [MatchVisibility.PUBLISHED],
 *   write [Match.publishedAt], and sync the match to the remote backend.
 * - "Share Match" will generate or display [Match.shareCode] for viewers.
 * - Visibility chip will allow toggling between PRIVATE / UNLISTED / PUBLISHED.
 */
@Composable
private fun MatchPublishingSection(match: Match) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Publishing",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        // Visibility status row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Match Visibility",
                style = MaterialTheme.typography.bodyMedium
            )
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text(
                    text = match.visibility.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // Publish Match button (disabled — backend not yet implemented)
        OutlinedButton(
            onClick = { /* TODO: implement backend publish */ },
            enabled = false,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Publish Match  ·  coming soon",
                style = MaterialTheme.typography.titleSmall
            )
        }

        // Share Match button (disabled — requires published match + backend)
        OutlinedButton(
            onClick = { /* TODO: implement share after publish */ },
            enabled = false,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Share Match  ·  coming soon",
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

// =============================================================================
// Add player to team dialog (shown from Match Details)
// =============================================================================

@Composable
private fun AddPlayerToTeamDialog(
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
