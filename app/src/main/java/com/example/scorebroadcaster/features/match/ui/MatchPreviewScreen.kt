package com.example.scorebroadcaster.features.match.ui
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.scorebroadcaster.features.scoring.data.InningsPhase
import com.example.scorebroadcaster.features.scoring.data.MatchState
import com.example.scorebroadcaster.features.scoring.data.ScoringConsoleState
import com.example.scorebroadcaster.features.scoring.data.BattingEntry
import com.example.scorebroadcaster.features.scoring.data.BowlingEntry
import com.example.scorebroadcaster.features.match.data.Match
import com.example.scorebroadcaster.features.scoring.domain.BallTimelineFormatter
import com.example.scorebroadcaster.features.scoring.domain.IndexedBall
import com.example.scorebroadcaster.features.scoring.domain.OverSummaryCalculator
import com.example.scorebroadcaster.core.theme.BoundarySixContainer
import com.example.scorebroadcaster.core.theme.OnBoundarySixContainer
import com.example.scorebroadcaster.features.scoring.viewmodel.MatchViewModel

/**
 * Read-only spectator-style preview of the live match.
 *
 * Shows live score, teams, batters, bowler, run rates, current over balls, and a
 * recent-event highlight.  Intentionally omits all scoring controls, undo, and
 * admin dialogs so the screen feels like a clean broadcast scoreboard.
 */
@Composable
fun MatchPreviewScreen(
    matchViewModel: MatchViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val state by matchViewModel.state.collectAsState()
    val console by matchViewModel.consoleState.collectAsState()
    val match by matchViewModel.activeMatch.collectAsState()
    val events by matchViewModel.events.collectAsState()

    val activeMatch: Match? = match

    if (activeMatch == null || console.phase == InningsPhase.SETUP) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No active match to preview.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        return
    }

    val currentOverBalls = BallTimelineFormatter.getCurrentOverBalls(events)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Score header card
        PreviewScoreCard(
            match = activeMatch,
            state = state,
            console = console
        )

        // 2. Run rate panel
        PreviewRunRateCard(
            state = state,
            console = console,
            oversLimit = activeMatch.overs
        )

        // 3. Batters section
        PreviewBattersCard(console = console)

        // 4. Bowler section
        console.currentBowlerEntry?.let { PreviewBowlerCard(entry = it) }

        // 5. Current over display
        if (currentOverBalls.isNotEmpty() ||
            console.phase == InningsPhase.FIRST_INNINGS ||
            console.phase == InningsPhase.SECOND_INNINGS
        ) {
            PreviewCurrentOverCard(balls = currentOverBalls)
        }

        // 6. Recent event highlight (last ball)
        val lastBall = currentOverBalls.lastOrNull()
        if (lastBall != null) {
            PreviewLastBallCard(ball = lastBall)
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

// =============================================================================
// Score header card
// =============================================================================

@Composable
private fun PreviewScoreCard(
    match: Match,
    state: MatchState,
    console: ScoringConsoleState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Match title: Team A vs Team B
            Text(
                text = match.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Batting team name
            Text(
                text = console.battingTeamName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            // Large score
            Text(
                text = "${state.runs}/${state.wickets}",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            // Overs
            Text(
                text = "Overs: ${state.overs}.${state.balls}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
            )

            // Target / chase info (second innings only)
            if (console.phase == InningsPhase.SECOND_INNINGS && console.target > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                )
                Text(
                    text = "Target: ${console.target}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                val runsNeeded = (console.target - state.runs).coerceAtLeast(0)
                val ballsBowled = state.overs * 6 + state.balls
                val ballsRemaining = (match.overs * 6 - ballsBowled).coerceAtLeast(0)
                if (state.runs < console.target) {
                    Text(
                        text = "Needs $runsNeeded runs from $ballsRemaining balls",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                } else {
                    Text(
                        text = "Target reached!",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

// =============================================================================
// Run rate panel
// =============================================================================

@Composable
private fun PreviewRunRateCard(
    state: MatchState,
    console: ScoringConsoleState,
    oversLimit: Int
) {
    val crr = ScorecardFormatter.formatRunRate(state.runs, state.overs, state.balls)

    val rrr: String? = if (console.phase == InningsPhase.SECOND_INNINGS && console.target > 0) {
        val runsNeeded = (console.target - state.runs).coerceAtLeast(0)
        val ballsBowled = state.overs * 6 + state.balls
        val ballsRemaining = (oversLimit * 6 - ballsBowled).coerceAtLeast(0)
        ScorecardFormatter.formatRequiredRunRate(runsNeeded, ballsRemaining)
    } else null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = if (rrr != null) Arrangement.SpaceAround else Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PreviewRunRateItem(label = "Current Run Rate", value = crr)
            if (rrr != null) {
                PreviewRunRateItem(label = "Required Run Rate", value = rrr)
            }
        }
    }
}

@Composable
private fun PreviewRunRateItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

// =============================================================================
// Batters section
// =============================================================================

@Composable
private fun PreviewBattersCard(console: ScoringConsoleState) {
    val striker = console.strikerEntry
    val nonStriker = console.nonStrikerEntry
    if (striker == null && nonStriker == null) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Batters",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            striker?.let { PreviewBatterRow(entry = it, isStriker = true) }
            nonStriker?.let { PreviewBatterRow(entry = it, isStriker = false) }
        }
    }
}

@Composable
private fun PreviewBatterRow(entry: BattingEntry, isStriker: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isStriker) "${entry.player.name} ★" else entry.player.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isStriker) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${entry.runs} (${entry.balls})",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isStriker) FontWeight.SemiBold else FontWeight.Normal
            )
            if (entry.fours > 0 || entry.sixes > 0) {
                Text(
                    text = "${entry.fours}×4  ${entry.sixes}×6",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
        }
    }
}

// =============================================================================
// Bowler section
// =============================================================================

@Composable
private fun PreviewBowlerCard(entry: BowlingEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Bowler",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = entry.player.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${ScorecardFormatter.formatOvers(entry.overs, entry.balls)} – ${entry.runs} – ${entry.wickets}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = "Overs – Runs – Wickets",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
        }
    }
}

// =============================================================================
// Current over display
// =============================================================================

@Composable
private fun PreviewCurrentOverCard(balls: List<IndexedBall>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Current Over",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            )
            if (balls.isEmpty()) {
                Text(
                    text = "New over starting…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    balls.forEach { indexedBall ->
                        PreviewBallChip(indexedBall = indexedBall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewBallChip(indexedBall: IndexedBall) {
    val label = OverSummaryCalculator.ballLabel(indexedBall.event)
    val bgColor = when {
        label == "W" || label.endsWith("W") -> MaterialTheme.colorScheme.error
        label == "4" -> MaterialTheme.colorScheme.secondaryContainer
        label == "6" -> BoundarySixContainer
        label.startsWith("Wd") || label.startsWith("Nb") -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        label == "W" || label.endsWith("W") -> MaterialTheme.colorScheme.onError
        label == "4" -> MaterialTheme.colorScheme.onSecondaryContainer
        label == "6" -> OnBoundarySixContainer
        label.startsWith("Wd") || label.startsWith("Nb") -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = bgColor, shape = MaterialTheme.shapes.small) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// =============================================================================
// Recent event highlight
// =============================================================================

@Composable
private fun PreviewLastBallCard(ball: IndexedBall) {
    val label = OverSummaryCalculator.ballLabel(ball.event)
    val displayText = when {
        label == "W" || label.endsWith("W") -> "WICKET"
        label == "4" -> "FOUR"
        label == "6" -> "SIX"
        label.startsWith("Wd") -> "WIDE"
        label.startsWith("Nb") -> "NO BALL"
        label == "0" -> "DOT"
        else -> "$label RUNS"
    }
    val containerColor = when {
        label == "W" || label.endsWith("W") -> MaterialTheme.colorScheme.errorContainer
        label == "4" -> MaterialTheme.colorScheme.secondaryContainer
        label == "6" -> BoundarySixContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        label == "W" || label.endsWith("W") -> MaterialTheme.colorScheme.onErrorContainer
        label == "4" -> MaterialTheme.colorScheme.onSecondaryContainer
        label == "6" -> OnBoundarySixContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Last Ball",
                style = MaterialTheme.typography.labelMedium,
                color = contentColor.copy(alpha = 0.7f)
            )
            Text(
                text = displayText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}
