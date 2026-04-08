package com.example.scorebroadcaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.scorebroadcaster.features.scoring.data.InningsPhase
import com.example.scorebroadcaster.navigation.AppShell
import com.example.scorebroadcaster.features.scoring.ui.BallTimelineScreen
import com.example.scorebroadcaster.features.streaming.ui.CameraPreviewScreen
import com.example.scorebroadcaster.features.match.ui.CreateMatchScreen
import com.example.scorebroadcaster.features.home.ui.HomeScreen
import com.example.scorebroadcaster.features.home.ui.LiveHubScreen
import com.example.scorebroadcaster.features.match.ui.MatchDetailsScreen
import com.example.scorebroadcaster.features.match.ui.MatchPreviewScreen
import com.example.scorebroadcaster.features.scoring.ui.MatchSummaryScreen
import com.example.scorebroadcaster.features.match.ui.MyMatchesScreen
import com.example.scorebroadcaster.features.match.ui.PlayerSetupScreen
import com.example.scorebroadcaster.features.players.ui.MyPlayersScreen
import com.example.scorebroadcaster.features.teams.ui.SavedTeamsScreen
import com.example.scorebroadcaster.features.scoring.ui.ScorecardScreen
import com.example.scorebroadcaster.features.auth.ui.ForgotPasswordScreen
import com.example.scorebroadcaster.features.scoring.ui.ScoringScreen
import com.example.scorebroadcaster.features.auth.ui.SignInScreen
import com.example.scorebroadcaster.features.auth.ui.SignUpScreen
import com.example.scorebroadcaster.features.streaming.ui.StreamPreviewScreen
import com.example.scorebroadcaster.features.streaming.ui.StreamSetupScreen
import com.example.scorebroadcaster.core.theme.ScoreBroadcasterTheme
import com.example.scorebroadcaster.features.auth.viewmodel.AuthViewModel
import com.example.scorebroadcaster.features.streaming.viewmodel.LiveStreamViewModel
import com.example.scorebroadcaster.features.match.viewmodel.MatchSessionViewModel
import com.example.scorebroadcaster.features.scoring.viewmodel.MatchViewModel
import com.example.scorebroadcaster.navigation.ScoreEmptyState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScoreBroadcasterTheme {
                val authViewModel: AuthViewModel = viewModel()
                val isSessionChecked by authViewModel.isSessionChecked.collectAsState()
                val isAuthenticated by authViewModel.isAuthenticated.collectAsState()

                when {
                    // ── Session restore in progress — show a centered spinner ──────────────
                    !isSessionChecked -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    // ── Not signed in — show auth flow ────────────────────────────────────
                    !isAuthenticated -> {
                        val authNavController = rememberNavController()
                        NavHost(
                            navController = authNavController,
                            startDestination = "sign_in"
                        ) {
                            composable("sign_in") {
                                SignInScreen(
                                    authViewModel = authViewModel,
                                    onNavigateToSignUp = {
                                        authNavController.navigate("sign_up") {
                                            launchSingleTop = true
                                        }
                                    },
                                    onNavigateToForgotPassword = {
                                        authNavController.navigate("forgot_password") {
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }
                            composable("sign_up") {
                                SignUpScreen(
                                    authViewModel = authViewModel,
                                    onNavigateToSignIn = {
                                        authNavController.popBackStack()
                                    }
                                )
                            }
                            composable("forgot_password") {
                                ForgotPasswordScreen(
                                    authViewModel = authViewModel,
                                    onNavigateToSignIn = {
                                        authNavController.popBackStack()
                                    }
                                )
                            }
                        }
                    }

                    // ── Signed in — show main app ─────────────────────────────────────────
                    else -> {
                        val matchViewModel: MatchViewModel = viewModel()
                        val liveStreamViewModel: LiveStreamViewModel = viewModel()
                        val matchSessionViewModel: MatchSessionViewModel = viewModel()
                        val navController = rememberNavController()

                        // Trigger player sync once the profile is available after sign-in.
                        val currentProfile by authViewModel.currentProfile.collectAsState()
                        LaunchedEffect(currentProfile) {
                            val profileId = currentProfile?.id ?: return@LaunchedEffect
                            matchSessionViewModel.syncPlayersForUser(profileId)
                            matchSessionViewModel.syncTeamsForUser(profileId)
                            matchSessionViewModel.syncMatchesForUser(profileId)
                        }

                        val activeMatch by matchSessionViewModel.activeMatch.collectAsState()
                        val resumableMatch by matchSessionViewModel.resumableMatch.collectAsState()
                        val savedPlayers by matchSessionViewModel.savedPlayers.collectAsState()
                        val scoringState by matchViewModel.state.collectAsState()
                        val scoringConsole by matchViewModel.consoleState.collectAsState()

                        // Build a live score summary string when there is an ongoing scoring session.
                        val scoreSummary: String? =
                            if (matchViewModel.activeMatch.collectAsState().value?.id == activeMatch?.id &&
                                scoringConsole.phase != InningsPhase.SETUP &&
                                scoringConsole.phase != InningsPhase.MATCH_COMPLETE &&
                                activeMatch != null
                            ) {
                                val inningsPart =
                                    if (scoringConsole.phase == InningsPhase.SECOND_INNINGS) "2nd inn"
                                    else "1st inn"
                                "${scoringState.runs}/${scoringState.wickets}  " +
                                        "(${scoringState.overs}.${scoringState.balls})  $inningsPart"
                            } else null

                        AppShell(
                            navController = navController,
                            onSignOut = { authViewModel.signOut() },
                            signedInEmail = authViewModel.currentUserEmail.collectAsState().value,
                            modifier = Modifier.fillMaxSize()
                        ) { paddingValues ->
                            NavHost(
                                navController = navController,
                                startDestination = "home",
                                modifier = Modifier.padding(paddingValues)
                            ) {
                                // ---- Primary tab destinations ----

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
                                        onResetMatchClick = {
                                            matchViewModel.resetMatch()
                                            matchSessionViewModel.clearActiveMatch()
                                        },
                                        onViewMatchDetails = { navController.navigate("match_details") },
                                        onViewScorecard = { navController.navigate("scorecard") },
                                        onResumeMatchClick = {
                                            val match = matchSessionViewModel.resumableMatch.value
                                            if (match != null) {
                                                matchSessionViewModel.setActiveMatch(match)
                                                matchViewModel.initFromMatch(match)
                                                navController.navigate("scoring_only")
                                            }
                                        },
                                        activeMatch = activeMatch,
                                        resumableMatch = if (activeMatch == null) resumableMatch else null,
                                        scoreSummary = scoreSummary
                                    )
                                }

                                // Score tab — renders ScoringScreen inline if there is an active match,
                                // otherwise shows a friendly empty state.
                                composable("score_tab") {
                                    val match = activeMatch
                                    if (match != null) {
                                        if (matchViewModel.activeMatch.collectAsState().value?.id != match.id) {
                                            matchViewModel.initFromMatch(match)
                                        }
                                        ScoringScreen(
                                            matchViewModel = matchViewModel,
                                            matchSessionViewModel = matchSessionViewModel,
                                            savedPlayers = savedPlayers,
                                            onSavePrivatePlayer = { matchSessionViewModel.addSavedPlayer(it) },
                                            onMatchDetails = { navController.navigate("match_details") },
                                            onViewScorecard = { navController.navigate("scorecard") },
                                            onCameraPreview = { navController.navigate("live_preview") },
                                            onViewTimeline = { navController.navigate("ball_timeline") },
                                            onPreviewMatch = { navController.navigate("match_preview") }
                                        )
                                    } else {
                                        ScoreEmptyState(
                                            onCreateMatchClick = { navController.navigate("create_match") },
                                            onMyMatchesClick = {
                                                matchSessionViewModel.refresh()
                                                navController.navigate("my_matches")
                                            }
                                        )
                                    }
                                }

                                composable("live_hub") {
                                    LiveHubScreen(
                                        onCameraPreviewClick = {
                                            if (activeMatch != null) navController.navigate("live_preview")
                                            else navController.navigate("create_match")
                                        },
                                        onStreamSetupClick = { navController.navigate("stream_setup") },
                                        onCreateMatchClick = { navController.navigate("create_match") },
                                        onMyMatchesClick = {
                                            matchSessionViewModel.refresh()
                                            navController.navigate("my_matches")
                                        },
                                        activeMatch = activeMatch
                                    )
                                }

                                // ---- Match creation flow ----

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

                                // ---- Matches section ----

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
                                        onViewTimeline = { navController.navigate("ball_timeline") },
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                                composable("scorecard") {
                                    ScorecardScreen(
                                        matchViewModel = matchViewModel,
                                        matchSessionViewModel = matchSessionViewModel
                                    )
                                }
                                composable("ball_timeline") {
                                    BallTimelineScreen(
                                        matchViewModel = matchViewModel
                                    )
                                }

                                // ---- Match Preview ----

                                composable("match_preview") {
                                    MatchPreviewScreen(
                                        matchViewModel = matchViewModel
                                    )
                                }

                                // ---- Saved Teams ----

                                composable("saved_teams") {
                                    SavedTeamsScreen(
                                        matchSessionViewModel = matchSessionViewModel
                                    )
                                }

                                // ---- My Players ----

                                composable("saved_players") {
                                    MyPlayersScreen(
                                        matchSessionViewModel = matchSessionViewModel
                                    )
                                }

                                // ---- Scoring ----

                                composable("scoring_only") {
                                    ScoringScreen(
                                        matchViewModel = matchViewModel,
                                        matchSessionViewModel = matchSessionViewModel,
                                        savedPlayers = savedPlayers,
                                        onSavePrivatePlayer = { matchSessionViewModel.addSavedPlayer(it) },
                                        onMatchDetails = { navController.navigate("match_details") },
                                        onViewScorecard = { navController.navigate("scorecard") },
                                        onCameraPreview = { navController.navigate("live_preview") },
                                        onViewTimeline = { navController.navigate("ball_timeline") },
                                        onPreviewMatch = { navController.navigate("match_preview") }
                                    )
                                }

                                // ---- Broadcast ----

                                composable("live_preview") {
                                    CameraPreviewScreen(
                                        onBack = { navController.popBackStack() },
                                        matchViewModel = matchViewModel
                                    )
                                }
                                composable("stream_setup") {
                                    StreamSetupScreen(
                                        liveStreamViewModel = liveStreamViewModel,
                                        onNavigateToPreview = { navController.navigate("stream_preview") }
                                    )
                                }
                                composable("stream_preview") {
                                    StreamPreviewScreen(
                                        onBack = { navController.popBackStack() },
                                        liveStreamViewModel = liveStreamViewModel
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

