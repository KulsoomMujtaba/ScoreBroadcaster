package com.example.scorebroadcaster.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.scorebroadcaster.data.ScoreEvent
import com.example.scorebroadcaster.viewmodel.MatchViewModel
import com.example.scorebroadcaster.data.entity.DismissalDetail

/**
 * A full-screen camera preview with a [ScoreboardOverlay] anchored to the bottom and a live
 * scoring controls panel anchored to the top.
 *
 * - Uses CameraX [PreviewView] embedded via [AndroidView] inside Compose.
 * - Binds the camera use-case to the host [androidx.lifecycle.LifecycleOwner] so that
 *   the camera starts/stops automatically with the screen lifecycle.
 * - The overlay reacts to every [com.example.scorebroadcaster.data.MatchState] emission
 *   from [MatchViewModel.state] via [collectAsStateWithLifecycle], recomposing whenever
 *   the match state changes.
 * - The scoring controls panel at the top lets the scorer record deliveries without leaving
 *   the camera view; buttons are wired directly to [MatchViewModel] methods.
 * - Requests the CAMERA permission at runtime if it has not already been granted.
 */
@Composable
fun CameraPreviewScreen(
    matchViewModel: MatchViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val state by matchViewModel.state.collectAsStateWithLifecycle()
    val console by matchViewModel.consoleState.collectAsStateWithLifecycle()
    val activeMatch by matchViewModel.activeMatch.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraPermissionGranted = granted
    }

    LaunchedEffect(Unit) {
        if (!cameraPermissionGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // PreviewView is remembered at the top level so it survives permission-state recompositions.
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (cameraPermissionGranted) {
            // Bind / unbind the camera use-case together with the lifecycle owner so that
            // the camera starts when the screen enters the foreground and stops when it leaves.
            DisposableEffect(lifecycleOwner) {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                val listener = Runnable {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview
                        )
                    } catch (e: Exception) {
                        Log.e("CameraPreviewScreen", "Failed to bind camera use-case", e)
                    }
                }
                cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))

                onDispose {
                    try {
                        cameraProviderFuture.get().unbindAll()
                    } catch (e: Exception) {
                        Log.w("CameraPreviewScreen", "Failed to unbind camera on dispose", e)
                    }
                }
            }

            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = "Camera permission is required to show the preview",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // ScoreboardOverlay updates automatically whenever MatchState changes
        ScoreboardOverlay(
            state = state,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Scoring controls panel overlaid at the top of the camera preview
        var showWicketDialog by remember { mutableStateOf(false) }
        ScoringControlsPanel(
            onEvent = { matchViewModel.addEvent(it) },
            onWicket = { showWicketDialog = true },
            onUndo = { matchViewModel.undo() },
            modifier = Modifier.align(Alignment.TopCenter)
        )
        if (showWicketDialog) {
            val bowlingTeamPlayers = if (console.inningsNumber == 1) {
                activeMatch?.bowlingFirst?.players.orEmpty()
            } else {
                activeMatch?.battingFirst?.players.orEmpty()
            }
            WicketDetailsDialog(
                striker = console.striker,
                nonStriker = console.nonStriker,
                bowlingTeamPlayers = bowlingTeamPlayers,
                currentBowler = console.currentBowler,
                onConfirm = { dismissal: DismissalDetail ->
                    showWicketDialog = false
                    matchViewModel.addEvent(ScoreEvent.Wicket(dismissal))
                },
                onDismiss = { showWicketDialog = false }
            )
        }
    }
}

/**
 * A compact row of scoring buttons displayed on top of the camera preview.
 *
 * Buttons: 0, 1, 2, 3, 4, 6, Wicket, Wide (+1), NoBall (+1), Undo.
 * Each button immediately updates [MatchViewModel] so the [ScoreboardOverlay] reflects the
 * new score without leaving the camera view.
 */
@Composable
private fun ScoringControlsPanel(
    onEvent: (ScoreEvent) -> Unit,
    onWicket: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xCC000000))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(0, 1, 2, 3, 4, 6).forEach { runs ->
            Button(
                onClick = { onEvent(ScoreEvent.Run(runs)) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("$runs")
            }
        }
        Button(
            onClick = onWicket,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCC0000)),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text("W")
        }
        Button(
            onClick = { onEvent(ScoreEvent.Wide(0)) },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            // Wide(0) → reducer always adds +1 penalty run; label shows the outcome
            Text("Wd+1")
        }
        Button(
            onClick = { onEvent(ScoreEvent.NoBall(0)) },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            // NoBall(0) → reducer always adds +1 penalty run; label shows the outcome
            Text("NB+1")
        }
        Button(
            onClick = onUndo,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text("Undo")
        }
    }
}
