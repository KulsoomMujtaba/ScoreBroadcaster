package com.devhub.scored.features.viewer.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devhub.scored.features.match.data.Match
import com.devhub.scored.features.scoring.data.MatchState
import com.devhub.scored.features.viewer.viewmodel.MatchViewerViewModel

/**
 * Read-only viewer screen for a published match.
 *
 * Displays:
 * - Match title and status badge
 * - Team names, format, overs info
 * - First-innings score (runs/wickets, overs)
 * - Second-innings score if available (runs/wickets, overs, target)
 * - Recent ball indicators from the last 6 deliveries
 *
 * This screen has NO scoring buttons, NO undo, and NO edit options.
 * All data is fetched from Supabase and reconstructed via [MatchViewerViewModel].
 */
@Composable
fun MatchViewerScreen(
    shareCode: String,
    viewerViewModel: MatchViewerViewModel = viewModel(),
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(shareCode) {
        viewerViewModel.loadMatchByShareCode(shareCode)
    }

    DisposableEffect(viewerViewModel) {
        onDispose {
            viewerViewModel.stopLiveUpdates()
        }
    }

    val loadState by viewerViewModel.loadState.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        when (val state = loadState) {
            is MatchViewerViewModel.ViewerLoadState.Idle,
            is MatchViewerViewModel.ViewerLoadState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            is MatchViewerViewModel.ViewerLoadState.Error -> {
                ViewerErrorContent(
                    message = state.message,
                    onBack = onBack,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            is MatchViewerViewModel.ViewerLoadState.Success -> {
                ViewerContent(
                    match = state.match,
                    firstInningsState = state.firstInningsState,
                    secondInningsState = state.secondInningsState,
                    hasSecondInnings = state.secondInningsEvents.isNotEmpty(),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// =============================================================================
// Error state
// =============================================================================

@Composable
private fun ViewerErrorContent(
    message: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Match Not Found",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Button(onClick = onBack) {
            Text("Go Back")
        }
    }
}

// =============================================================================
// Success state
// =============================================================================

@Composable
private fun ViewerContent(
    match: Match,
    firstInningsState: MatchState,
    secondInningsState: MatchState?,
    hasSecondInnings: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Read-only badge
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(
                text = "👁  View Only",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }

        // Match title
        Text(
            text = match.displayTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        HorizontalDivider()

        // Match info
        ViewerInfoRow("Format", "${match.format.label.substringBefore(" (")} · ${match.overs} overs")
        ViewerInfoRow("Toss", match.tossResultText)
        ViewerInfoRow("Bat 1st", match.battingFirst.name)
        ViewerInfoRow("Bowl 1st", match.bowlingFirst.name)

        HorizontalDivider()

        // 1st innings scorecard
        ViewerInningsCard(
            inningsLabel = "1st Innings",
            teamName = match.battingFirst.name,
            state = firstInningsState,
            isActive = !hasSecondInnings,
            target = null
        )

        // 2nd innings scorecard (shown when second innings has started)
        if (hasSecondInnings && secondInningsState != null) {
            Spacer(Modifier.height(4.dp))
            val target = firstInningsState.runs + 1
            ViewerInningsCard(
                inningsLabel = "2nd Innings",
                teamName = match.bowlingFirst.name,
                state = secondInningsState,
                isActive = true,
                target = target
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ViewerInningsCard(
    inningsLabel: String,
    teamName: String,
    state: MatchState,
    isActive: Boolean,
    target: Int?
) {
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = inningsLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                if (isActive) {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = "● Live",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Text(
                text = teamName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )

            // Score
            Text(
                text = "${state.runs}/${state.wickets}",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "${state.overs}.${state.balls} overs",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            // Target + runs needed
            if (target != null) {
                val runsNeeded = (target - state.runs).coerceAtLeast(0)
                val result = when {
                    state.runs >= target -> "${teamName} win by ${10 - state.wickets} wickets"
                    state.wickets >= 10 && state.runs < target -> "All out — $runsNeeded runs short"
                    else -> "Need $runsNeeded more to win · Target $target"
                }
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Recent balls strip (last 6)
            if (state.lastBalls.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.lastBalls.forEach { label ->
                        BallChip(label = label)
                    }
                }
            }

            // Extras
            if (state.extras > 0) {
                Text(
                    text = "Extras: ${state.extras}  (W:${state.wides} NB:${state.noBalls} B:${state.byes} LB:${state.legByes})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun BallChip(label: String) {
    val isWicket = label == "W"
    val isBoundary = label == "4" || label == "6"
    val containerColor = when {
        isWicket -> MaterialTheme.colorScheme.errorContainer
        isBoundary -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        isWicket -> MaterialTheme.colorScheme.onErrorContainer
        isBoundary -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun ViewerInfoRow(label: String, value: String) {
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
