package com.example.scorebroadcaster.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
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
    modifier: Modifier = Modifier
) {
    val activeMatch by matchSessionViewModel.activeMatch.collectAsState()
    val scoringMatch by matchViewModel.activeMatch.collectAsState()
    val state by matchViewModel.state.collectAsState()
    val console by matchViewModel.consoleState.collectAsState()

    val match = activeMatch ?: scoringMatch

    if (match == null || console.phase == InningsPhase.SETUP) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
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

    // Resolve 1st innings data
    val firstBatting: List<BattingEntry>
    val firstBowling: List<BowlingEntry>
    val firstExtras: Int
    val firstWides: Int
    val firstNoBalls: Int
    val firstByes: Int
    val firstLegByes: Int
    val firstRuns: Int
    val firstWickets: Int
    val firstOvers: Int
    val firstBalls: Int

    if (console.phase == InningsPhase.FIRST_INNINGS) {
        // Still in first innings — show live data
        firstBatting = console.allBattingEntries
        firstBowling = console.allBowlingEntries
        firstExtras = state.extras
        firstWides = state.wides
        firstNoBalls = state.noBalls
        firstByes = state.byes
        firstLegByes = state.legByes
        firstRuns = state.runs
        firstWickets = state.wickets
        firstOvers = state.overs
        firstBalls = state.balls
    } else {
        // After first innings — use saved snapshot
        firstBatting = console.firstInningsBattingEntries
        firstBowling = console.firstInningsBowlingEntries
        firstExtras = console.firstInningsExtras
        firstWides = console.firstInningsWides
        firstNoBalls = console.firstInningsNoBalls
        firstByes = console.firstInningsByes
        firstLegByes = console.firstInningsLegByes
        firstRuns = console.firstInningsRuns
        firstWickets = console.firstInningsWickets
        firstOvers = console.firstInningsOvers
        firstBalls = console.firstInningsBalls
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // --- Match header ---
        item { ScorecardMatchHeader(match = match) }

        item { HorizontalDivider() }

        // --- 1st innings ---
        item {
            InningsScorecardSection(
                title = "1st Innings — ${match.battingFirst.name}",
                battingEntries = firstBatting,
                bowlingEntries = firstBowling,
                extras = firstExtras,
                wides = firstWides,
                noBalls = firstNoBalls,
                byes = firstByes,
                legByes = firstLegByes,
                totalRuns = firstRuns,
                totalWickets = firstWickets,
                totalOvers = firstOvers,
                totalBalls = firstBalls
            )
        }

        // --- 2nd innings (if started) ---
        if (console.phase == InningsPhase.SECOND_INNINGS ||
            console.phase == InningsPhase.MATCH_COMPLETE
        ) {
            item { HorizontalDivider() }

            // Target info
            item {
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
            }

            item {
                InningsScorecardSection(
                    title = "2nd Innings — ${match.bowlingFirst.name}",
                    battingEntries = console.allBattingEntries,
                    bowlingEntries = console.allBowlingEntries,
                    extras = state.extras,
                    wides = state.wides,
                    noBalls = state.noBalls,
                    byes = state.byes,
                    legByes = state.legByes,
                    totalRuns = state.runs,
                    totalWickets = state.wickets,
                    totalOvers = state.overs,
                    totalBalls = state.balls
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
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
    wides: Int,
    noBalls: Int,
    byes: Int,
    legByes: Int,
    totalRuns: Int,
    totalWickets: Int,
    totalOvers: Int,
    totalBalls: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        // --- BATTING SCORECARD ---
        Text(
            "BATTING",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
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

        // --- Extras breakdown ---
        ExtrasRow(
            total = extras,
            wides = wides,
            noBalls = noBalls,
            byes = byes,
            legByes = legByes
        )

        // --- Totals row ---
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
                "$totalRuns/$totalWickets  (${ScorecardFormatter.formatOvers(totalOvers, totalBalls)} ov)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // --- BOWLING FIGURES ---
        if (bowlingEntries.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "BOWLING",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            BowlingTable(entries = bowlingEntries)
        }
    }
}

// =============================================================================
// Extras breakdown row
// =============================================================================

@Composable
private fun ExtrasRow(
    total: Int,
    wides: Int,
    noBalls: Int,
    byes: Int,
    legByes: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Extras",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                "$total",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = "  (wd $wides, nb $noBalls, b $byes, lb $legByes)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

// =============================================================================
// Batting table
// =============================================================================

@Composable
private fun BattingTable(entries: List<BattingEntry>) {
    Column {
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
        listOf("R", "B", "4s", "6s", "SR").forEach { col ->
            Text(
                col,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(0.6f),
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
            "${entry.sixes}",
            ScorecardFormatter.formatStrikeRate(entry.runs, entry.balls)
        ).forEach { value ->
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(0.6f),
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
        listOf("O", "M", "R", "W", "Econ").forEach { col ->
            Text(
                col,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(0.6f),
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
            ScorecardFormatter.formatOvers(entry.overs, entry.balls),
            "${entry.maidens}",
            "${entry.runs}",
            "${entry.wickets}",
            ScorecardFormatter.formatEconomy(entry.runs, entry.overs, entry.balls)
        ).forEach { value ->
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(0.6f),
                textAlign = TextAlign.End
            )
        }
    }
}

