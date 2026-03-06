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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.scorebroadcaster.data.entity.Match
import com.example.scorebroadcaster.viewmodel.MatchSessionViewModel
import com.example.scorebroadcaster.viewmodel.MatchViewModel

@Composable
fun MatchSummaryScreen(
    matchSessionViewModel: MatchSessionViewModel,
    matchViewModel: MatchViewModel,
    onStartMatch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pending by matchSessionViewModel.pendingMatch.collectAsState()
    val match = pending ?: return

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Match Summary", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Review your match setup before starting.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        HorizontalDivider()

        SummaryRow(label = "Match", value = match.displayTitle)
        SummaryRow(label = "Format", value = "${match.format.label.substringBefore(" (")} • ${match.overs} overs")
        SummaryRow(label = "Toss", value = match.tossResultText)
        SummaryRow(label = "Batting first", value = match.battingFirst.name)
        SummaryRow(label = "Bowling first", value = match.bowlingFirst.name)

        HorizontalDivider()

        // Player summaries
        TeamPlayerSummary(match = match, teamIsA = true)
        TeamPlayerSummary(match = match, teamIsA = false)

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                matchSessionViewModel.confirmMatch(match)
                matchViewModel.initFromMatch(match)
                onStartMatch()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("▶  Start Match", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.weight(0.4f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@Composable
private fun TeamPlayerSummary(match: Match, teamIsA: Boolean) {
    val team = if (teamIsA) match.teamA else match.teamB
    val playerCount = team.players.size
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(team.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            if (playerCount == 0) {
                Text(
                    "No players added",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            } else {
                Text(
                    team.players.joinToString(" • ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}
