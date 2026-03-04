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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.scorebroadcaster.viewmodel.MatchViewModel

/**
 * A full-screen camera preview with a [ScoreboardOverlay] anchored to the bottom.
 *
 * - Uses CameraX [PreviewView] embedded via [AndroidView] inside Compose.
 * - Binds the camera use-case to the host [androidx.lifecycle.LifecycleOwner] so that
 *   the camera starts/stops automatically with the screen lifecycle.
 * - The overlay reacts to every [com.example.scorebroadcaster.data.MatchState] emission
 *   from [MatchViewModel.state], recomposing whenever the match state changes.
 * - Requests the CAMERA permission at runtime if it has not already been granted.
 */
@Composable
fun CameraPreviewScreen(
    matchViewModel: MatchViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val state by matchViewModel.state.collectAsState()
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
    }
}
