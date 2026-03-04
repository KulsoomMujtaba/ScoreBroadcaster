package com.example.scorebroadcaster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scorebroadcaster.data.MatchState

/**
 * A broadcast-style scoreboard overlay intended to be displayed on top of a live video stream.
 *
 * The overlay renders a bottom bar with:
 * - Left side: "TeamA vs TeamB"
 * - Right side: score ("123/4") and overs ("14.2 ov")
 *
 * When [state.lastBalls] is non-empty, a second row is shown with the recent delivery outcomes
 * (e.g. "1 0 4 W 1 .").
 *
 * The semi-transparent dark background ensures high contrast over any video content.
 */
@Composable
fun ScoreboardOverlay(
    state: MatchState,
    modifier: Modifier = Modifier
) {
    val overlayBackground = Color(0xCC000000) // ~80% opaque black
    val primaryText = Color.White
    val secondaryText = Color(0xFFDDDDDD)
    val wicketColor = Color(0xFFFF4444)
    val boundaryColor = Color(0xFF44AAFF)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(overlayBackground)
    ) {
        // Optional second row: last-ball indicators
        if (state.lastBalls.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                state.lastBalls.forEach { ball ->
                    val color = when (ball) {
                        "W" -> wicketColor
                        "4", "6" -> boundaryColor
                        else -> secondaryText
                    }
                    Text(
                        text = ball,
                        color = color,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Main broadcast bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: match title
            Text(
                text = "${state.teamAName} vs ${state.teamBName}",
                color = primaryText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )

            // Right: score + overs
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${state.runs}/${state.wickets}",
                    color = primaryText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${state.overs}.${state.balls} ov",
                    color = secondaryText,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

@Preview(showBackground = true, backgroundColor = 0xFF228B22) // green pitch background
@Composable
private fun ScoreboardOverlayPreview() {
    Box {
        ScoreboardOverlay(
            state = MatchState(
                teamAName = "India",
                teamBName = "Australia",
                runs = 123,
                wickets = 4,
                overs = 14,
                balls = 2,
                lastBalls = listOf("1", "0", "4", "W", "1", ".")
            )
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF228B22)
@Composable
private fun ScoreboardOverlayNoLastBallsPreview() {
    Box {
        ScoreboardOverlay(
            state = MatchState(
                teamAName = "England",
                teamBName = "Pakistan",
                runs = 56,
                wickets = 2,
                overs = 8,
                balls = 0
            )
        )
    }
}
