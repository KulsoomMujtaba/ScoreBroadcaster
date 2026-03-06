package com.example.scorebroadcaster.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.scorebroadcaster.data.entity.Match

/**
 * Live / Broadcast hub — the primary destination for the Live bottom-nav tab.
 *
 * When there is an [activeMatch]:
 * - Shows the active match info.
 * - Provides "Camera Preview" and "Go Live" (stream setup) action buttons.
 *
 * When there is no active match:
 * - Shows a helpful empty state explaining that a match is needed.
 * - Offers a CTA to create or select a match.
 */
@Composable
fun LiveHubScreen(
    onCameraPreviewClick: () -> Unit,
    onStreamSetupClick: () -> Unit,
    onCreateMatchClick: () -> Unit,
    onMyMatchesClick: () -> Unit,
    activeMatch: Match? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        if (activeMatch != null) {
            ActiveLiveHub(
                activeMatch = activeMatch,
                onCameraPreviewClick = onCameraPreviewClick,
                onStreamSetupClick = onStreamSetupClick
            )
        } else {
            NoMatchLiveHub(
                onCreateMatchClick = onCreateMatchClick,
                onMyMatchesClick = onMyMatchesClick
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Active match — broadcast hub
// ---------------------------------------------------------------------------

@Composable
private fun ActiveLiveHub(
    activeMatch: Match,
    onCameraPreviewClick: () -> Unit,
    onStreamSetupClick: () -> Unit
) {
    // Match info card
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "● Broadcasting",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
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
        }
    }

    Spacer(Modifier.height(24.dp))
    Text(
        text = "Broadcast Options",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )
    Spacer(Modifier.height(12.dp))

    // Camera Preview
    BroadcastOptionCard(
        title = "Camera Preview",
        description = "Preview the camera with live scoreboard overlay before going on air.",
        actionLabel = "Open Camera",
        onAction = onCameraPreviewClick
    )

    Spacer(Modifier.height(12.dp))

    // Go Live
    BroadcastOptionCard(
        title = "Go Live",
        description = "Set up RTMP stream and broadcast live with the scoreboard burned in.",
        actionLabel = "Stream Setup",
        onAction = onStreamSetupClick,
        isPrimary = true
    )
}

@Composable
private fun BroadcastOptionCard(
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
    isPrimary: Boolean = false
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(12.dp))
            if (isPrimary) {
                Button(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Text(actionLabel)
                }
            } else {
                OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                    Text(actionLabel)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// No active match — empty state
// ---------------------------------------------------------------------------

@Composable
private fun NoMatchLiveHub(
    onCreateMatchClick: () -> Unit,
    onMyMatchesClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
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
                    text = "You need an active match to use Camera Preview or Go Live. Create a new match or select an existing one to get started.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onMyMatchesClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "My Matches")
                    }
                    Button(
                        onClick = onCreateMatchClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "Create Match")
                    }
                }
            }
        }
    }
}
