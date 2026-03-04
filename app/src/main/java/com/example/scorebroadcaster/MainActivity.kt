package com.example.scorebroadcaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.scorebroadcaster.ui.CameraPreviewScreen
import com.example.scorebroadcaster.ui.theme.ScoreBroadcasterTheme
import com.example.scorebroadcaster.viewmodel.MatchViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScoreBroadcasterTheme {
                val matchViewModel: MatchViewModel = viewModel()
                Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
                    CameraPreviewScreen(matchViewModel = matchViewModel)
                }
            }
        }
    }
}