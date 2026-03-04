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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.scorebroadcaster.data.StreamConfig
import com.example.scorebroadcaster.data.StreamingStatus
import com.example.scorebroadcaster.viewmodel.LiveStreamViewModel

private val BITRATE_OPTIONS = listOf(2500, 3500, 4500)
private const val RESOLUTION_PRESET = "720p"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamSetupScreen(
    liveStreamViewModel: LiveStreamViewModel = viewModel(),
    onNavigateToPreview: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val streamingStatus by liveStreamViewModel.streamingStatus.collectAsStateWithLifecycle()

    var serverUrl by remember { mutableStateOf("") }
    var streamKey by remember { mutableStateOf("") }
    var selectedBitrate by remember { mutableStateOf(BITRATE_OPTIONS[0]) }
    var bitrateMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        serverUrl = liveStreamViewModel.getLastServerUrl()
        streamKey = liveStreamViewModel.getLastStreamKey()
    }

    val isStreaming = streamingStatus is StreamingStatus.Streaming ||
            streamingStatus is StreamingStatus.Connecting ||
            streamingStatus is StreamingStatus.Reconnecting

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "Stream Setup",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("Server URL") },
            placeholder = { Text("rtmps://live-api-s.facebook.com:443/rtmp") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            enabled = !isStreaming
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = streamKey,
            onValueChange = { streamKey = it },
            label = { Text("Stream Key") },
            supportingText = { Text("Leave blank if the stream key is already in the Server URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            enabled = !isStreaming
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = RESOLUTION_PRESET,
            onValueChange = {},
            label = { Text("Resolution") },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            enabled = false
        )
        Spacer(modifier = Modifier.height(12.dp))

        ExposedDropdownMenuBox(
            expanded = bitrateMenuExpanded,
            onExpandedChange = { if (!isStreaming) bitrateMenuExpanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = "$selectedBitrate kbps",
                onValueChange = {},
                label = { Text("Bitrate") },
                readOnly = true,
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bitrateMenuExpanded) },
                enabled = !isStreaming
            )
            ExposedDropdownMenu(
                expanded = bitrateMenuExpanded,
                onDismissRequest = { bitrateMenuExpanded = false }
            ) {
                BITRATE_OPTIONS.forEach { bitrate ->
                    DropdownMenuItem(
                        text = { Text("$bitrate kbps") },
                        onClick = {
                            selectedBitrate = bitrate
                            bitrateMenuExpanded = false
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    liveStreamViewModel.prepareStreaming(
                        StreamConfig(
                            serverUrl = serverUrl,
                            streamKey = streamKey,
                            resolutionPreset = RESOLUTION_PRESET,
                            bitrateKbps = selectedBitrate
                        )
                    )
                    onNavigateToPreview()
                },
                modifier = Modifier.weight(1f),
                enabled = !isStreaming && serverUrl.isNotBlank() && streamKey.isNotBlank()
            ) {
                Text("Start Streaming")
            }
            Button(
                onClick = { liveStreamViewModel.stopStreaming() },
                modifier = Modifier.weight(1f),
                enabled = isStreaming,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Stop Streaming")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        StreamingStatusDisplay(status = streamingStatus)
    }
}

@Composable
private fun StreamingStatusDisplay(status: StreamingStatus, modifier: Modifier = Modifier) {
    val (label, containerColor, contentColor) = when (status) {
        is StreamingStatus.Idle -> Triple(
            "Idle",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        is StreamingStatus.Connecting -> Triple(
            "Connecting…",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        is StreamingStatus.Streaming -> Triple(
            "Streaming",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        is StreamingStatus.Reconnecting -> Triple(
            "Reconnecting…",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        is StreamingStatus.Error -> Triple(
            "Error: ${status.message}",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(16.dp),
            color = contentColor,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
