package com.example.scorebroadcaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.scorebroadcaster.ui.CameraPreviewScreen
import com.example.scorebroadcaster.ui.CreateMatchScreen
import com.example.scorebroadcaster.ui.HomeScreen
import com.example.scorebroadcaster.ui.MyMatchesScreen
import com.example.scorebroadcaster.ui.ScoringScreen
import com.example.scorebroadcaster.ui.StreamPreviewScreen
import com.example.scorebroadcaster.ui.StreamSetupScreen
import com.example.scorebroadcaster.ui.theme.ScoreBroadcasterTheme
import com.example.scorebroadcaster.viewmodel.LiveStreamViewModel
import com.example.scorebroadcaster.viewmodel.MatchViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScoreBroadcasterTheme {
                val matchViewModel: MatchViewModel = viewModel()
                val liveStreamViewModel: LiveStreamViewModel = viewModel()
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                onCreateMatchClick = { navController.navigate("create_match") },
                                onMyMatchesClick = { navController.navigate("my_matches") },
                                onLiveScoringClick = { navController.navigate("live_preview") },
                                onGoLiveClick = { navController.navigate("stream_setup") },
                                onResetMatchClick = { matchViewModel.resetMatch() }
                            )
                        }
                        composable("create_match") {
                            CreateMatchScreen()
                        }
                        composable("my_matches") {
                            MyMatchesScreen()
                        }
                        composable("live_preview") {
                            CameraPreviewScreen(matchViewModel = matchViewModel)
                        }
                        composable("scoring_only") {
                            ScoringScreen(matchViewModel = matchViewModel)
                        }
                        composable("stream_setup") {
                            StreamSetupScreen(
                                liveStreamViewModel = liveStreamViewModel,
                                onNavigateToPreview = { navController.navigate("stream_preview") }
                            )
                        }
                        composable("stream_preview") {
                            StreamPreviewScreen(liveStreamViewModel = liveStreamViewModel)
                        }
                    }
                }
            }
        }
    }
}
