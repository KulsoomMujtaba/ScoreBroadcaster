package com.example.scorebroadcaster.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import com.example.scorebroadcaster.data.InningsPhase
import com.example.scorebroadcaster.data.entity.Match
import com.example.scorebroadcaster.data.entity.MatchStatus
import com.example.scorebroadcaster.viewmodel.MatchSessionViewModel
import com.example.scorebroadcaster.viewmodel.MatchViewModel

@Composable
fun MyMatchesScreen(
    matchSessionViewModel: MatchSessionViewModel,
    matchViewModel: MatchViewModel,
    onMatchClick: (Match) -> Unit,
    onCreateMatchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val matches by matchSessionViewModel.matches.collectAsState()
    val activeMatch by matchSessionViewModel.activeMatch.collectAsState()
    val scoringMatch by matchViewModel.activeMatch.collectAsState()
    val scoringState by matchViewModel.state.collectAsState()
    val scoringConsole by matchViewModel.consoleState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text("My Matches", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        if (matches.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "No matches yet.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Create your first match to get started.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onCreateMatchClick) {
                    Text("Create Match")
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(matches, key = { it.id }) { match ->
                    val isActive = activeMatch?.id == match.id
                    val isScoringMatch = scoringMatch?.id == match.id
                    val liveScore: String? = if (isScoringMatch &&
                        scoringConsole.phase != InningsPhase.SETUP
                    ) {
                        val inningsPart = if (scoringConsole.phase == InningsPhase.SECOND_INNINGS ||
                            scoringConsole.phase == InningsPhase.MATCH_COMPLETE
                        ) "2nd inn" else "1st inn"
                        "${scoringState.runs}/${scoringState.wickets}  (${scoringState.overs}.${scoringState.balls})  $inningsPart"
                    } else null

                    MatchListItem(
                        match = match,
                        isActive = isActive,
                        liveScore = liveScore,
                        onClick = { onMatchClick(match) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchListItem(
    match: Match,
    isActive: Boolean,
    liveScore: String?,
    onClick: () -> Unit
) {
    Surface(
        tonalElevation = if (isActive) 6.dp else 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    match.displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                StatusChip(status = match.status, isActive = isActive)
            }
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                "${match.format.label.substringBefore(" (")} • ${match.overs} overs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            if (liveScore != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    liveScore,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Tap to open match hub",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            } else if (match.status == MatchStatus.IN_PROGRESS) {
                Text(
                    "Tap to open match hub",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun StatusChip(status: MatchStatus, isActive: Boolean) {
    val label = if (isActive && status == MatchStatus.IN_PROGRESS) "● Live" else status.label
    val color = when {
        isActive && status == MatchStatus.IN_PROGRESS -> MaterialTheme.colorScheme.error
        status == MatchStatus.COMPLETED -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
