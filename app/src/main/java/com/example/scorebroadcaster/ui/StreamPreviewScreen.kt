package com.example.scorebroadcaster.ui

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.scorebroadcaster.data.StreamingStatus
import com.example.scorebroadcaster.viewmodel.LiveStreamViewModel
import com.example.scorebroadcaster.viewmodel.MatchViewModel
import com.pedro.library.view.OpenGlView

private val STREAMING_PERMISSIONS = arrayOf(
    Manifest.permission.CAMERA,
    Manifest.permission.RECORD_AUDIO
)

/**
 * Full-screen camera preview that streams to an RTMP endpoint.
 *
 * - Uses [RtmpLiveStreamer] (via [LiveStreamViewModel]) to open the camera and push H.264+AAC
 *   to the URL configured in [StreamSetupScreen].
 * - Requests CAMERA and RECORD_AUDIO permissions at runtime if not yet granted.
 * - Shows a red "● LIVE" badge while the stream is active.
 * - Streaming stops automatically when this screen is disposed (back-press / navigation pop).
 */
@Composable
fun StreamPreviewScreen(
    liveStreamViewModel: LiveStreamViewModel = viewModel(),
    matchViewModel: MatchViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val streamingStatus by liveStreamViewModel.streamingStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var permissionsGranted by remember {
        mutableStateOf(
            STREAMING_PERMISSIONS.all { perm ->
                ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
    }

    // OpenGlView is remembered so it survives recompositions; RtmpCamera2 renders into it.
    val openGlView = remember {
        OpenGlView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    // Request permissions on first entry; effect won't restart on recomposition.
    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            permissionLauncher.launch(STREAMING_PERMISSIONS)
        }
    }

    // Start streaming once permissions are confirmed; stop automatically when screen is popped.
    if (permissionsGranted) {
        DisposableEffect(Unit) {
            openGlView.post {
                liveStreamViewModel.startStreaming(openGlView, matchViewModel.state)
            }
            onDispose { liveStreamViewModel.stopStreaming() }
        }
    }

    val status = streamingStatus
    val isLive = status is StreamingStatus.Streaming

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (permissionsGranted) {
            AndroidView(
                factory = { openGlView },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = "Camera and microphone permissions are required to stream",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            )
        }

        // LIVE badge – visible only while the connection is confirmed
        if (isLive) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                color = Color.Red,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "● LIVE",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        // Status text + Stop button anchored to the bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xCC000000))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = when (status) {
                    is StreamingStatus.Idle -> "Ready"
                    is StreamingStatus.Connecting -> "Connecting…"
                    is StreamingStatus.Streaming -> "● Streaming"
                    is StreamingStatus.Reconnecting -> "Reconnecting…"
                    is StreamingStatus.Error -> "Error: ${status.message}"
                },
                color = if (isLive) Color(0xFF66FF66) else Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = { liveStreamViewModel.stopStreaming() },
                enabled = status !is StreamingStatus.Idle,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Stop Streaming")
            }
        }
    }
}
