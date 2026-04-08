package com.example.scorebroadcaster.features.viewer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Entry screen for the read-only viewer flow.
 *
 * The user types a share code (6–8 alphanumeric characters) and taps "Watch Match"
 * to navigate to [MatchViewerScreen].  No Supabase call is made here — the load
 * happens inside [MatchViewerScreen] via [MatchViewerViewModel].
 */
@Composable
fun EnterShareCodeScreen(
    onWatchMatch: (shareCode: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var code by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 40.dp),
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
                    text = "Watch a Match",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Enter the share code provided by the scorer to view a live or completed match in read-only mode.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(20.dp))

                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(8) },
                    label = { Text("Share Code") },
                    placeholder = { Text("e.g. AB1C2D3") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onWatchMatch(code.trim()) },
                    enabled = code.trim().length >= 6,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Watch Match")
                }
            }
        }
    }
}
