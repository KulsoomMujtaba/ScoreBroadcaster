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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.scorebroadcaster.data.entity.Match

/**
 * Home dashboard — the first screen users see after launching Scored.
 *
 * When there is an [activeMatch]:
 * - Shows an active-match card with match info and an optional live [scoreSummary].
 * - Provides quick-action buttons: Resume Scoring, Match Details, Scorecard,
 *   Camera Preview, Go Live, and Reset Match.
 *
 * When there is no active match:
 * - Shows a welcome / empty-state section with a primary "Create Match" CTA
 *   and a secondary "My Matches" link.
 */
@Composable
fun HomeScreen(
    onCreateMatchClick: () -> Unit,
    onMyMatchesClick: () -> Unit,
    onLiveScoringClick: () -> Unit,
    onCameraPreviewClick: () -> Unit,
    onGoLiveClick: () -> Unit,
    onResetMatchClick: () -> Unit,
    onViewMatchDetails: () -> Unit = {},
    onViewScorecard: () -> Unit = {},
    activeMatch: Match? = null,
    scoreSummary: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        if (activeMatch != null) {
            ActiveMatchDashboard(
                activeMatch = activeMatch,
                scoreSummary = scoreSummary,
                onLiveScoringClick = onLiveScoringClick,
                onViewMatchDetails = onViewMatchDetails,
                onViewScorecard = onViewScorecard,
                onCameraPreviewClick = onCameraPreviewClick,
                onGoLiveClick = onGoLiveClick,
                onResetMatchClick = onResetMatchClick
            )
        } else {
            EmptyStateDashboard(
                onCreateMatchClick = onCreateMatchClick,
                onMyMatchesClick = onMyMatchesClick
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Active match dashboard
// ---------------------------------------------------------------------------

@Composable
private fun ActiveMatchDashboard(
    activeMatch: Match,
    scoreSummary: String?,
    onLiveScoringClick: () -> Unit,
    onViewMatchDetails: () -> Unit,
    onViewScorecard: () -> Unit,
    onCameraPreviewClick: () -> Unit,
    onGoLiveClick: () -> Unit,
    onResetMatchClick: () -> Unit
) {
    // Active match card
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "● Live",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = activeMatch.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${activeMatch.format.label.substringBefore(" (")} • ${activeMatch.overs} overs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            if (scoreSummary != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = scoreSummary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Current score",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                )
            }
        }
    }

    Spacer(Modifier.height(24.dp))
    Text(
        text = "Quick Actions",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )
    Spacer(Modifier.height(8.dp))

    // Primary action
    Button(
        onClick = onLiveScoringClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = "Resume Scoring", style = MaterialTheme.typography.titleMedium)
    }
    Spacer(Modifier.height(8.dp))

    // Secondary actions
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onViewMatchDetails,
            modifier = Modifier.weight(1f)
        ) {
            Text(text = "Match Details")
        }
        OutlinedButton(
            onClick = onViewScorecard,
            modifier = Modifier.weight(1f)
        ) {
            Text(text = "Scorecard")
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onCameraPreviewClick,
            modifier = Modifier.weight(1f)
        ) {
            Text(text = "Camera Preview")
        }
        Button(
            onClick = onGoLiveClick,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary
            )
        ) {
            Text(text = "Go Live")
        }
    }

    Spacer(Modifier.height(24.dp))
    HorizontalDivider()
    Spacer(Modifier.height(8.dp))

    TextButton(
        onClick = onResetMatchClick,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = "Reset Match")
    }
}

// ---------------------------------------------------------------------------
// Empty-state dashboard
// ---------------------------------------------------------------------------

@Composable
private fun EmptyStateDashboard(
    onCreateMatchClick: () -> Unit,
    onMyMatchesClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Scored",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Cricket Scoring",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(40.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No active match",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Create a new match to start scoring ball by ball, preview with camera, or go live.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onCreateMatchClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Create Match",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onMyMatchesClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "My Matches")
                }
            }
        }
    }
}
