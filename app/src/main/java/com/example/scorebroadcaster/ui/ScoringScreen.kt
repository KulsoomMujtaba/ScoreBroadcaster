package com.example.scorebroadcaster.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.scorebroadcaster.data.ScoreEvent
import com.example.scorebroadcaster.viewmodel.MatchViewModel

@Composable
fun ScoringScreen(matchViewModel: MatchViewModel = viewModel(), modifier: Modifier = Modifier) {
    val state by matchViewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "${state.teamAName} vs ${state.teamBName}",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${state.runs}/${state.wickets}",
            style = MaterialTheme.typography.displayMedium
        )
        Text(
            text = "Overs: ${state.overs}.${state.balls}",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "Extras: ${state.extras}",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Last 6 deliveries
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            state.lastBalls.forEach { ball ->
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = ball,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Run buttons: 0 1 2 3 4 6
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 1, 2, 3, 4, 6).forEach { runs ->
                Button(onClick = { matchViewModel.addEvent(ScoreEvent.Run(runs)) }) {
                    Text("$runs")
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Wicket / Wide / NoBall
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { matchViewModel.addEvent(ScoreEvent.Wicket) }) {
                Text("Wicket")
            }
            Button(onClick = { matchViewModel.addEvent(ScoreEvent.Wide(0)) }) {
                Text("Wide")
            }
            Button(onClick = { matchViewModel.addEvent(ScoreEvent.NoBall(0)) }) {
                Text("No Ball")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { matchViewModel.undo() },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Undo")
        }
    }
}
