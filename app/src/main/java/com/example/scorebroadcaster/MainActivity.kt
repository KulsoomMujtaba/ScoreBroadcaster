package com.example.scorebroadcaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.scorebroadcaster.ui.CameraPreviewScreen
import com.example.scorebroadcaster.ui.CreateMatchScreen
import com.example.scorebroadcaster.ui.HomeScreen
import com.example.scorebroadcaster.ui.MatchDetailsScreen
import com.example.scorebroadcaster.ui.MatchSummaryScreen
import com.example.scorebroadcaster.ui.MyMatchesScreen
import com.example.scorebroadcaster.ui.PlayerSetupScreen
import com.example.scorebroadcaster.ui.ScorecardScreen
import com.example.scorebroadcaster.ui.ScoringScreen
import com.example.scorebroadcaster.ui.StreamPreviewScreen
import com.example.scorebroadcaster.ui.StreamSetupScreen
import com.example.scorebroadcaster.ui.theme.ScoreBroadcasterTheme
import com.example.scorebroadcaster.viewmodel.LiveStreamViewModel
import com.example.scorebroadcaster.viewmodel.MatchSessionViewModel
import com.example.scorebroadcaster.viewmodel.MatchViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScoreBroadcasterTheme {
                val matchViewModel: MatchViewModel = viewModel()
                val liveStreamViewModel: LiveStreamViewModel = viewModel()
                val matchSessionViewModel: MatchSessionViewModel = viewModel()
                val navController = rememberNavController()

                val activeMatch by matchSessionViewModel.activeMatch.collectAsState()

                Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                onCreateMatchClick = { navController.navigate("create_match") },
                                onMyMatchesClick = {
                                    matchSessionViewModel.refresh()
                                    navController.navigate("my_matches")
                                },
                                onLiveScoringClick = {
                                    val match = activeMatch
                                    if (match != null) {
                                        if (matchViewModel.activeMatch.value == null) {
                                            matchViewModel.initFromMatch(match)
                                        }
                                        navController.navigate("scoring_only")
                                    } else {
                                        navController.navigate("create_match")
                                    }
                                },
                                onCameraPreviewClick = {
                                    if (activeMatch != null) navController.navigate("live_preview")
                                    else navController.navigate("create_match")
                                },
                                onGoLiveClick = { navController.navigate("stream_setup") },
                                onResetMatchClick = { matchViewModel.resetMatch() },
                                activeMatch = activeMatch
                            )
                        }
                        composable("create_match") {
                            CreateMatchScreen(
                                matchSessionViewModel = matchSessionViewModel,
                                onNavigateToPlayers = { navController.navigate("player_setup") }
                            )
                        }
                        composable("player_setup") {
                            PlayerSetupScreen(
                                matchSessionViewModel = matchSessionViewModel,
                                onNavigateToSummary = { navController.navigate("match_summary") }
                            )
                        }
                        composable("match_summary") {
                            MatchSummaryScreen(
                                matchSessionViewModel = matchSessionViewModel,
                                matchViewModel = matchViewModel,
                                onStartMatch = {
                                    navController.navigate("scoring_only") {
                                        popUpTo("home")
                                    }
                                }
                            )
                        }
                        composable("my_matches") {
                            MyMatchesScreen(
                                matchSessionViewModel = matchSessionViewModel,
                                matchViewModel = matchViewModel,
                                onMatchClick = { match ->
                                    matchSessionViewModel.setActiveMatch(match)
                                    navController.navigate("match_details")
                                },
                                onCreateMatchClick = { navController.navigate("create_match") }
                            )
                        }
                        composable("match_details") {
                            MatchDetailsScreen(
                                matchSessionViewModel = matchSessionViewModel,
                                matchViewModel = matchViewModel,
                                onStartScoring = {
                                    val match = matchSessionViewModel.activeMatch.value
                                    if (match != null &&
                                        matchViewModel.activeMatch.value?.id != match.id
                                    ) {
                                        matchViewModel.initFromMatch(match)
                                    }
                                    navController.navigate("scoring_only")
                                },
                                onCameraPreview = { navController.navigate("live_preview") },
                                onGoLive = { navController.navigate("stream_setup") },
                                onViewScorecard = { navController.navigate("scorecard") },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("scorecard") {
                            ScorecardScreen(
                                matchViewModel = matchViewModel,
                                matchSessionViewModel = matchSessionViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("live_preview") {
                            CameraPreviewScreen(matchViewModel = matchViewModel)
                        }
                        composable("scoring_only") {
                            ScoringScreen(
                                matchViewModel = matchViewModel,
                                onMatchDetails = { navController.navigate("match_details") },
                                onViewScorecard = { navController.navigate("scorecard") },
                                onCameraPreview = { navController.navigate("live_preview") }
                            )
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

