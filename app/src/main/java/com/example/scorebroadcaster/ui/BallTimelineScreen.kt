package com.example.scorebroadcaster.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.scorebroadcaster.data.InningsPhase
import com.example.scorebroadcaster.domain.BallTimelineFormatter
import com.example.scorebroadcaster.domain.IndexedBall
import com.example.scorebroadcaster.domain.OverSummary
import com.example.scorebroadcaster.viewmodel.MatchViewModel

/**
 * Ball timeline / over history screen.
 *
 * Displays all deliveries for the active innings (or both innings for a completed/in-progress
 * two-innings match) grouped into over cards.
 *
 * Architecture notes:
 * - All grouping logic lives in [BallTimelineFormatter]; this composable only renders results.
 * - Each ball item carries a stable [globalIndex] so future edit-ball support can be wired
 *   without a structural change.
 * - A [LazyColumn] is used so the screen handles arbitrarily long innings efficiently.
 */
@Composable
fun BallTimelineScreen(
    matchViewModel: MatchViewModel,
    modifier: Modifier = Modifier
) {
    val console    by matchViewModel.consoleState.collectAsState()
    val match      by matchViewModel.activeMatch.collectAsState()

    // Current-innings events (second innings if started, otherwise first innings)
    val currentEvents by matchViewModel.events.collectAsState()

    // First-innings snapshot (populated once first innings ends)
    val firstEvents   by matchViewModel.firstInningsEvents.collectAsState()

    // Show the tab switcher only when the second innings has actually started
    val hasSecondInnings = console.phase == InningsPhase.SECOND_INNINGS ||
            console.phase == InningsPhase.MATCH_COMPLETE

    // Which innings tab is selected (0 = 1st, 1 = 2nd); default to current innings
    var selectedInnings by remember { mutableIntStateOf(if (hasSecondInnings) 1 else 0) }

    // Determine the event list to display based on selected tab
    val displayEvents = when {
        !hasSecondInnings -> currentEvents                    // only one innings
        selectedInnings == 0 -> firstEvents                  // first innings tab
        else -> currentEvents                                 // second innings tab
    }

    val overs = BallTimelineFormatter.groupByOver(displayEvents)

    // Derive team names for the innings tab labels
    val firstInningsTeam  = match?.battingFirst?.name  ?: console.battingTeamName
    val secondInningsTeam = match?.bowlingFirst?.name  ?: ""

    if (match == null || console.phase == InningsPhase.SETUP) {
        EmptyTimelineState(modifier = modifier)
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // --- Innings tab switcher (shown only when a second innings is available) ---
        if (hasSecondInnings) {
            item {
                InningsTabRow(
                    selectedInnings  = selectedInnings,
                    firstTeamName    = firstInningsTeam,
                    secondTeamName   = secondInningsTeam,
                    onSelect         = { selectedInnings = it }
                )
            }
        }

        if (overs.isEmpty()) {
            item {
                Text(
                    text = "No deliveries recorded yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            items(items = overs, key = { it.overNumber }) { over ->
                OverCard(over = over)
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// =============================================================================
// Innings tab row
// =============================================================================

@Composable
private fun InningsTabRow(
    selectedInnings: Int,
    firstTeamName: String,
    secondTeamName: String,
    onSelect: (Int) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected  = selectedInnings == 0,
            onClick   = { onSelect(0) },
            label     = { Text("1st Inn${if (firstTeamName.isNotBlank()) " · $firstTeamName" else ""}") }
        )
        FilterChip(
            selected  = selectedInnings == 1,
            onClick   = { onSelect(1) },
            label     = { Text("2nd Inn${if (secondTeamName.isNotBlank()) " · $secondTeamName" else ""}") }
        )
    }
}

// =============================================================================
// Over card
// =============================================================================

@Composable
private fun OverCard(over: OverSummary) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Over label
            Text(
                text       = "Over ${over.overNumber}",
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.primary
            )

            // Ball chips in a wrapping row
            BallChipsRow(balls = over.balls)
        }
    }
}

// =============================================================================
// Ball chips row
// =============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BallChipsRow(balls: List<IndexedBall>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement   = Arrangement.spacedBy(6.dp)
    ) {
        balls.forEach { ball ->
            BallChip(ball = ball)
        }
    }
}

// =============================================================================
// Single ball chip
// =============================================================================

/**
 * Compact chip representing a single delivery.
 *
 * The chip is currently display-only, but its [ball] parameter carries a stable
 * [IndexedBall.globalIndex] so that a future "tap to edit" gesture can be wired here
 * without any structural change to the surrounding data model.
 */
@Composable
private fun BallChip(ball: IndexedBall) {
    val isWicket     = ball.event.wicket
    val isExtra      = ball.event.extras.wides > 0 || ball.event.extras.noBalls > 0 ||
            ball.event.extras.byes > 0 || ball.event.extras.legByes > 0
    val isBoundary   = ball.event.runsOffBat >= 4 && !isExtra

    val containerColor = when {
        isWicket   -> MaterialTheme.colorScheme.errorContainer
        isBoundary -> MaterialTheme.colorScheme.primaryContainer
        isExtra    -> MaterialTheme.colorScheme.tertiaryContainer
        else       -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        isWicket   -> MaterialTheme.colorScheme.onErrorContainer
        isBoundary -> MaterialTheme.colorScheme.onPrimaryContainer
        isExtra    -> MaterialTheme.colorScheme.onTertiaryContainer
        else       -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        modifier = Modifier.size(width = 40.dp, height = 36.dp)
    ) {
        Column(
            modifier           = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text       = ball.display,
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color      = contentColor,
                textAlign  = TextAlign.Center,
                maxLines   = 1
            )
        }
    }
}

// =============================================================================
// Empty state
// =============================================================================

@Composable
private fun EmptyTimelineState(modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text       = "Ball timeline will be available once scoring starts.",
            style      = MaterialTheme.typography.bodyLarge,
            textAlign  = TextAlign.Center,
            color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}
