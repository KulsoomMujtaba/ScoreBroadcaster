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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.scorebroadcaster.data.InningsPhase
import com.example.scorebroadcaster.data.ScoringConsoleState
import com.example.scorebroadcaster.data.entity.BattingEntry
import com.example.scorebroadcaster.data.entity.BowlingEntry
import com.example.scorebroadcaster.data.entity.Match
import com.example.scorebroadcaster.viewmodel.MatchSessionViewModel
import com.example.scorebroadcaster.viewmodel.MatchViewModel

@Composable
fun ScorecardScreen(
    matchViewModel: MatchViewModel,
    matchSessionViewModel: MatchSessionViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeMatch by matchSessionViewModel.activeMatch.collectAsState()
    val scoringMatch by matchViewModel.activeMatch.collectAsState()
    val state by matchViewModel.state.collectAsState()
    val console by matchViewModel.consoleState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // --- Top bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Scorecard",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        val match = activeMatch ?: scoringMatch
        if (match == null || console.phase == InningsPhase.SETUP) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Scorecard will be available once scoring starts.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            return
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Match header ---
            ScorecardMatchHeader(match = match)

            HorizontalDivider()

            // --- 1st innings ---
            val firstBatting: List<BattingEntry>
            val firstBowling: List<BowlingEntry>
            val firstExtras: Int
            val firstRuns: Int
            val firstWickets: Int

            if (console.phase == InningsPhase.FIRST_INNINGS) {
                // Still in first innings — show live data
                firstBatting = console.allBattingEntries
                firstBowling = console.allBowlingEntries
                firstExtras = state.extras
                firstRuns = state.runs
                firstWickets = state.wickets
            } else {
                // After first innings — use saved snapshot
                firstBatting = console.firstInningsBattingEntries
                firstBowling = console.firstInningsBowlingEntries
                firstExtras = console.firstInningsExtras
                firstRuns = console.firstInningsRuns
                firstWickets = console.firstInningsWickets
            }

            InningsScorecardSection(
                title = "1st Innings — ${match.battingFirst.name}",
                battingEntries = firstBatting,
                bowlingEntries = firstBowling,
                extras = firstExtras,
                totalRuns = firstRuns,
                totalWickets = firstWickets
            )

            // --- 2nd innings (if started) ---
            if (console.phase == InningsPhase.SECOND_INNINGS ||
                console.phase == InningsPhase.MATCH_COMPLETE
            ) {
                HorizontalDivider()

                // Target info
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val runsNeeded = (console.target - state.runs).coerceAtLeast(0)
                    val chaseText = when {
                        console.phase == InningsPhase.MATCH_COMPLETE && state.runs >= console.target ->
                            "${match.bowlingFirst.name} won by ${10 - state.wickets} wickets"
                        console.phase == InningsPhase.MATCH_COMPLETE ->
                            "${match.battingFirst.name} won by ${(runsNeeded - 1).coerceAtLeast(0)} runs"
                        else -> "Target: ${console.target}  •  Need $runsNeeded"
                    }
                    Text(
                        text = chaseText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                InningsScorecardSection(
                    title = "2nd Innings — ${match.bowlingFirst.name}",
                    battingEntries = console.allBattingEntries,
                    bowlingEntries = console.allBowlingEntries,
                    extras = state.extras,
                    totalRuns = state.runs,
                    totalWickets = state.wickets
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// =============================================================================
// Match header card
// =============================================================================

@Composable
private fun ScorecardMatchHeader(match: Match) {
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = match.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${match.format.label.substringBefore(" (")} · ${match.overs} overs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = match.tossResultText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

// =============================================================================
// Single innings scorecard section
// =============================================================================

@Composable
private fun InningsScorecardSection(
    title: String,
    battingEntries: List<BattingEntry>,
    bowlingEntries: List<BowlingEntry>,
    extras: Int,
    totalRuns: Int,
    totalWickets: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        if (battingEntries.isEmpty()) {
            Text(
                "No batting data available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        } else {
            BattingTable(entries = battingEntries)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Extras",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text("$extras", style = MaterialTheme.typography.bodySmall)
        }

        HorizontalDivider(thickness = 0.5.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Total",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "$totalRuns/$totalWickets",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (bowlingEntries.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Bowling",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            BowlingTable(entries = bowlingEntries)
        }
    }
}

// =============================================================================
// Batting table
// =============================================================================

@Composable
private fun BattingTable(entries: List<BattingEntry>) {
    Column {
        // Header
        BattingTableHeader()
        HorizontalDivider(thickness = 0.5.dp)
        entries.forEach { entry ->
            BattingTableRow(entry = entry)
        }
    }
}

@Composable
private fun BattingTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "Batter",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(2f)
        )
        listOf("R", "B", "4s", "6s").forEach { col ->
            Text(
                col,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(0.5f),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun BattingTableRow(entry: BattingEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(2f)) {
            Text(
                text = entry.player.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            if (entry.isOut) {
                val dismissalText = entry.dismissal?.toScorecardString() ?: "out"
                Text(
                    dismissalText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                    "not out",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        listOf(
            "${entry.runs}",
            "${entry.balls}",
            "${entry.fours}",
            "${entry.sixes}"
        ).forEach { value ->
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(0.5f),
                textAlign = TextAlign.End
            )
        }
    }
}

// =============================================================================
// Bowling table
// =============================================================================

@Composable
private fun BowlingTable(entries: List<BowlingEntry>) {
    Column {
        BowlingTableHeader()
        HorizontalDivider(thickness = 0.5.dp)
        entries.forEach { entry ->
            BowlingTableRow(entry = entry)
        }
    }
}

@Composable
private fun BowlingTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "Bowler",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(2f)
        )
        listOf("O", "R", "W").forEach { col ->
            Text(
                col,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(0.5f),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun BowlingTableRow(entry: BowlingEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = entry.player.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(2f)
        )
        listOf(
            "${entry.overs}.${entry.balls}",
            "${entry.runs}",
            "${entry.wickets}"
        ).forEach { value ->
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(0.5f),
                textAlign = TextAlign.End
            )
        }
    }
}
