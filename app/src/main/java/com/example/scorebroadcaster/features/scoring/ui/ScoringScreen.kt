package com.example.scorebroadcaster.features.scoring.ui
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.scorebroadcaster.features.scoring.data.InningsPhase
import com.example.scorebroadcaster.features.scoring.data.MatchState
import com.example.scorebroadcaster.features.scoring.data.PendingAction
import com.example.scorebroadcaster.features.scoring.data.ScoreEvent
import com.example.scorebroadcaster.features.scoring.data.ScoringConsoleState
import com.example.scorebroadcaster.features.scoring.data.BattingEntry
import com.example.scorebroadcaster.features.scoring.data.BowlingEntry
import com.example.scorebroadcaster.features.scoring.data.DismissalDetail
import com.example.scorebroadcaster.features.scoring.data.DismissalType
import com.example.scorebroadcaster.features.scoring.data.ExtrasBreakdown
import com.example.scorebroadcaster.features.match.data.Match
import com.example.scorebroadcaster.features.players.data.Player
import com.example.scorebroadcaster.features.players.data.PlayerProfile
import com.example.scorebroadcaster.features.teams.data.Team
import com.example.scorebroadcaster.features.players.data.toMatchPlayer
import com.example.scorebroadcaster.features.scoring.domain.BallEvent
import com.example.scorebroadcaster.features.scoring.domain.BallTimelineFormatter
import com.example.scorebroadcaster.features.scoring.domain.IndexedBall
import com.example.scorebroadcaster.features.scoring.domain.OverSummary
import com.example.scorebroadcaster.features.scoring.domain.OverSummaryCalculator
import com.example.scorebroadcaster.features.scoring.viewmodel.MatchViewModel
import com.example.scorebroadcaster.features.match.viewmodel.MatchSessionViewModel
import com.example.scorebroadcaster.core.theme.BoundaryFourContainer
import com.example.scorebroadcaster.core.theme.OnBoundaryFourContainer
import com.example.scorebroadcaster.core.theme.BoundarySixContainer
import com.example.scorebroadcaster.core.theme.OnBoundarySixContainer
import com.example.scorebroadcaster.core.theme.NormalRunContainer
import com.example.scorebroadcaster.core.theme.OnNormalRunContainer
import com.example.scorebroadcaster.features.players.ui.PlayerPickerDialog
import com.example.scorebroadcaster.features.players.ui.normalizePlayerName
import com.example.scorebroadcaster.features.players.ui.sameIdentityAs
import com.example.scorebroadcaster.features.scoring.data.Innings
import com.example.scorebroadcaster.features.scoring.data.Partnership

/** Tabs shown in the top navigation of [ScoringScreen]. */
enum class ScoringScreenTab(val title: String) {
    SCORE("Score"),
    TIMELINE("Timeline"),
    SCORECARD("Scorecard")
}

/** Identifies which type of extra delivery the scorer is entering. */
enum class ExtraType(val label: String) {
    WIDE("Wide"),
    NO_BALL("No Ball"),
    BYE("Bye"),
    LEG_BYE("Leg Bye"),
}

@Composable
fun ScoringScreen(
    matchViewModel: MatchViewModel = viewModel(),
    matchSessionViewModel: MatchSessionViewModel = viewModel(),
    /** Saved private player profiles to offer in the picker during match-time flows. */
    savedPlayers: List<PlayerProfile> = emptyList(),
    /**
     * Called whenever a new private [PlayerProfile] is created during match-time flows
     * (e.g. "Add new player" in the batter/bowler dialog).  The caller persists the
     * profile via [com.example.scorebroadcaster.viewmodel.MatchSessionViewModel.addSavedPlayer].
     */
    onSavePrivatePlayer: (PlayerProfile) -> Unit = {},
    onMatchDetails: () -> Unit = {},
    onViewScorecard: () -> Unit = {},
    onCameraPreview: () -> Unit = {},
    onViewTimeline: () -> Unit = {},
    onPreviewMatch: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by matchViewModel.state.collectAsState()
    val console by matchViewModel.consoleState.collectAsState()
    val match by matchViewModel.activeMatch.collectAsState()
    val events by matchViewModel.events.collectAsState()
    val isResuming by matchViewModel.isResuming.collectAsState()
    // Capture a non-nullable snapshot so inner lambdas and blocks can smart-cast.
    val activeMatch: Match? = match

    // Show openers-setup dialog when setup is genuinely required.
    // Setup is required when:
    //  - The innings phase is SETUP (fresh innings, never started), OR
    //  - The innings is active (FIRST_INNINGS / SECOND_INNINGS) but setup was never completed.
    //
    // IMPORTANT: do NOT treat "zero deliveries after undo" as missing setup.
    // inningsSetupCompleted is set to true by setOpeners() and is never cleared by undo,
    // so it correctly distinguishes "setup never done" from "setup done, first ball undone".
    //
    // Also suppressed while isResuming is true: the ViewModel's async resume coroutine may
    // transiently expose a FIRST_INNINGS / setupCompleted=false state before the persisted
    // events are loaded and state is fully reconstructed.
    val needsInningsSetup = !isResuming && when (console.phase) {
        InningsPhase.SETUP -> true
        InningsPhase.FIRST_INNINGS,
        InningsPhase.SECOND_INNINGS ->
            !console.inningsSetupCompleted &&
                console.pendingAction !is PendingAction.SelectNextBatter
        else -> false
    }

    var setupDialogVisible by remember { mutableStateOf(false) }
    LaunchedEffect(needsInningsSetup) {
        if (needsInningsSetup && activeMatch != null) {
            setupDialogVisible = true
            Log.d("SetupFlow", "Setup required: phase=${console.phase}, " +
                    "striker=${console.striker?.name}, " +
                    "nonStriker=${console.nonStriker?.name}, " +
                    "bowler=${console.currentBowler?.name}")
        } else if (!needsInningsSetup) {
            // State was restored (e.g. after async resume from DB) — dismiss any
            // setup dialog that may have opened during the transient null phase.
            setupDialogVisible = false
        }
    }

    // Tab selection state — survives recomposition without resetting scoring state
    var selectedTab by rememberSaveable { mutableStateOf(ScoringScreenTab.SCORE) }

    // Undo message snackbar
    val undoMessage by matchViewModel.undoMessage.collectAsState()
    val validationError by matchViewModel.validationError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(undoMessage) {
        val msg = undoMessage
        if (!msg.isNullOrEmpty()) {
            snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
            matchViewModel.clearUndoMessage()
        }
    }
    LaunchedEffect(validationError) {
        val err = validationError
        if (!err.isNullOrEmpty()) {
            snackbarHostState.showSnackbar(message = err, duration = SnackbarDuration.Short)
            matchViewModel.clearValidationError()
        }
    }

    // Controls the manual result selection dialog shown when the scorer ends the match
    // before it is naturally completed (win / all-out / overs-limit).
    var showManualResultDialog by remember { mutableStateOf(false) }

    // State for the dismissible select-bowler bottom sheet.
    // Auto-opens when the over ends and SelectBowler becomes pending; can be dismissed
    // to unblock navigation while scoring remains gated until a bowler is chosen.
    var showSelectBowlerSheet by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(console.pendingAction is PendingAction.SelectBowler) {
        showSelectBowlerSheet = console.pendingAction is PendingAction.SelectBowler
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // --- Compact match header (always visible above tabs) ---
            CompactMatchHeader(
                match = activeMatch,
                state = state,
                consoleState = console,
                onPreviewMatch = onPreviewMatch
            )

            // --- Tab navigation ---
            if (activeMatch != null) {
                ScrollableTabRow(selectedTabIndex = selectedTab.ordinal) {
                    ScoringScreenTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = { Text(tab.title) }
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    ScoringScreenTab.TIMELINE -> BallTimelineScreen(
                        matchViewModel = matchViewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                    ScoringScreenTab.SCORECARD -> ScorecardScreen(
                        matchViewModel = matchViewModel,
                        matchSessionViewModel = matchSessionViewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                    ScoringScreenTab.SCORE -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

            // --- Innings break ---
            if (console.phase == InningsPhase.INNINGS_BREAK && activeMatch != null) {
                InningsBreakSection(
                    battingFirstTeam = activeMatch.battingFirst.name,
                    firstInningsRuns = console.firstInningsRuns,
                    firstInningsWickets = console.firstInningsWickets,
                    target = console.target,
                    onStartSecondInnings = { matchViewModel.startSecondInnings() },
                    onViewScorecard = onViewScorecard
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // --- Innings ball history ribbon ---
            BallHistoryRibbon(overs = BallTimelineFormatter.groupByOver(events))
            Spacer(modifier = Modifier.height(12.dp))

            // --- Current players card ---
            if (console.phase == InningsPhase.FIRST_INNINGS ||
                console.phase == InningsPhase.SECOND_INNINGS
            ) {
                PlayersSection(
                    console = console,
                    onSwapStrike = { matchViewModel.swapStrike() }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // --- Innings setup required banner ---
            if (needsInningsSetup && !setupDialogVisible && activeMatch != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Innings setup required before scoring can begin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { setupDialogVisible = true }) {
                            Text(
                                "Setup",
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // --- Next bowler required banner ---
            // Shown when an over has ended and bowler selection is pending but the
            // bottom sheet has been dismissed.  Keeps scoring clearly blocked without
            // trapping the user in a modal dialog.
            if (console.pendingAction is PendingAction.SelectBowler && !showSelectBowlerSheet) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Next bowler required",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Select the bowler for the new over before continuing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        TextButton(
                            onClick = { showSelectBowlerSheet = true },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Text("Select Bowler")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // --- Scoring buttons ---
            // Scoring is disabled when:
            //  - setup is still pending (players not identified), OR
            //  - the innings has consumed all allocated overs (isInningsOver).
            // The overs-limit check here is a UX guard only; the domain-level enforcement
            // lives in MatchViewModel.addBallEvent() and the ScoreReducer.
            val scoringEnabled = (console.phase == InningsPhase.FIRST_INNINGS ||
                    console.phase == InningsPhase.SECOND_INNINGS) &&
                    console.pendingAction == null &&
                    console.striker != null &&
                    !state.isInningsOver
            // Penalty runs can be awarded whenever an innings is in progress —
            // including after the overs limit is reached — but not during setup or match-complete.
            val penaltyEnabled = console.phase == InningsPhase.FIRST_INNINGS ||
                    console.phase == InningsPhase.SECOND_INNINGS
            // Wicket details dialog state — shown before dispatching the Wicket event
            var showWicketDialog by remember { mutableStateOf(false) }
            // Extras entry dialog state
            var extrasDialogType by remember { mutableStateOf<ExtraType?>(null) }
            // Overthrow delivery dialog state
            var showOverthrowDialog by remember { mutableStateOf(false) }
            // Penalty runs dialog state
            var showPenaltyDialog by remember { mutableStateOf(false) }
            ScoringButtonsSection(
                onEvent = { matchViewModel.addEvent(it) },
                onUndo = { matchViewModel.undo() },
                onWicket = { showWicketDialog = true },
                onExtras = { type -> extrasDialogType = type },
                onOverthrows = { showOverthrowDialog = true },
                onPenaltyRuns = { showPenaltyDialog = true },
                enabled = scoringEnabled,
                penaltyEnabled = penaltyEnabled
            )
            if (showWicketDialog) {
                val bowlingTeamPlayers = when {
                    activeMatch == null -> emptyList()
                    console.inningsNumber == 1 -> activeMatch.bowlingFirst.players
                    else -> activeMatch.battingFirst.players
                }
                WicketDetailsDialog(
                    striker = console.striker,
                    nonStriker = console.nonStriker,
                    bowlingTeamPlayers = bowlingTeamPlayers,
                    currentBowler = console.currentBowler,
                    onConfirm = { dismissal, runsCompleted ->
                        showWicketDialog = false
                        matchViewModel.addEvent(ScoreEvent.Wicket(dismissal, runsCompleted))
                    },
                    onDismiss = { showWicketDialog = false }
                )
            }
            val currentExtrasType = extrasDialogType
            if (currentExtrasType != null) {
                val bowlingTeamPlayers = when {
                    activeMatch == null -> emptyList()
                    console.inningsNumber == 1 -> activeMatch.bowlingFirst.players
                    else -> activeMatch.battingFirst.players
                }
                when (currentExtrasType) {
                    ExtraType.WIDE, ExtraType.NO_BALL -> WideNoBallEntryDialog(
                        type = currentExtrasType,
                        onConfirm = { ballEvent ->
                            extrasDialogType = null
                            matchViewModel.addBallEvent(ballEvent)
                        },
                        onDismiss = { extrasDialogType = null }
                    )
                    ExtraType.BYE, ExtraType.LEG_BYE -> ByeLegByeEntryDialog(
                        type = currentExtrasType,
                        onConfirm = { ballEvent ->
                            extrasDialogType = null
                            matchViewModel.addBallEvent(ballEvent)
                        },
                        onDismiss = { extrasDialogType = null }
                    )
                }
            }
            // Overthrow delivery dialog
            if (showOverthrowDialog) {
                OverthrowRunDialog(
                    onConfirm = { ballEvent ->
                        showOverthrowDialog = false
                        matchViewModel.addBallEvent(ballEvent)
                    },
                    onDismiss = { showOverthrowDialog = false }
                )
            }
            // Penalty runs dialog
            if (showPenaltyDialog) {
                PenaltyRunsDialog(
                    onConfirm = { runs ->
                        showPenaltyDialog = false
                        matchViewModel.addPenaltyRuns(runs)
                    },
                    onDismiss = { showPenaltyDialog = false }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // --- Innings / match control ---
            if (activeMatch != null) {
                InningsControlSection(
                    console = console,
                    onEndFirstInnings = { matchViewModel.endFirstInnings() },
                    onEndMatch = { showManualResultDialog = true }
                )
            }

            // Manual result selection dialog — shown when the scorer ends the match
            // before a natural completion (win / all-out / overs-limit).
            if (showManualResultDialog && activeMatch != null) {
                ManualResultSelectionDialog(
                    teamAName = activeMatch.teamA.name,
                    teamBName = activeMatch.teamB.name,
                    onResultSelected = { resultLabel ->
                        showManualResultDialog = false
                        matchViewModel.endMatchWithManualResult(resultLabel)
                    },
                    onDismiss = { showManualResultDialog = false }
                )
            }

            // --- Add player during match ---
            var showAddPlayerDialog by remember { mutableStateOf(false) }
            if (activeMatch != null &&
                (console.phase == InningsPhase.FIRST_INNINGS ||
                        console.phase == InningsPhase.SECOND_INNINGS)
            ) {
                TextButton(onClick = { showAddPlayerDialog = true }) {
                    Text(
                        "＋ Add player to team",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (showAddPlayerDialog) {
                    val battingTeam = if (console.inningsNumber == 1) activeMatch.battingFirst
                                      else activeMatch.bowlingFirst
                    val bowlingTeam = if (console.inningsNumber == 1) activeMatch.bowlingFirst
                                      else activeMatch.battingFirst
                    AddPlayerToMatchDialog(
                        battingTeamName = console.battingTeamName,
                        bowlingTeamName = console.bowlingTeamName,
                        battingTeamPlayers = battingTeam.players,
                        bowlingTeamPlayers = bowlingTeam.players,
                        savedPlayers = savedPlayers,
                        onDismiss = { showAddPlayerDialog = false },
                        onPickFromSaved = { profile, isNew, toBatting ->
                            if (isNew) onSavePrivatePlayer(profile)
                            matchViewModel.addPlayerToTeam(profile.toMatchPlayer(), toBatting)
                            showAddPlayerDialog = false
                        }
                    )
                }
            }

            // --- Match complete banner ---
            if (console.phase == InningsPhase.MATCH_COMPLETE) {
                Spacer(modifier = Modifier.height(16.dp))
                MatchCompleteSection(
                    runsScored = state.runs,
                    wickets = state.wickets,
                    console = console,
                    battingTeamName = state.teamAName,
                    bowlingTeamName = state.teamBName
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onViewScorecard) { Text("View Scorecard") }
                    OutlinedButton(onClick = onMatchDetails) { Text("Match Hub") }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Extras (always visible during play) ---
            if (console.phase == InningsPhase.FIRST_INNINGS ||
                console.phase == InningsPhase.SECOND_INNINGS
            ) {
                Text(
                    text = "Extras: ${state.extras}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
                    } // closes ScoringScreenTab.SCORE Column
                } // closes when (selectedTab)
            } // closes Box(weight(1f))
        } // closes outer Column

        // --- Pending action dialogs (rendered on top of everything) ---
        when (val action = console.pendingAction) {
            is PendingAction.SelectNextBatter -> {
                val title = if (action.replacingStriker) "Select Next Batter" else "Select Next Non-Striker"
                Log.d("WicketFlow", "Next batter dialog shown (${action.teamPlayers.size} eligible team players, replacingStriker=${action.replacingStriker})")
                val opposingPlayers = when {
                    activeMatch == null -> emptyList()
                    console.inningsNumber == 1 -> activeMatch.bowlingFirst.players
                    else -> activeMatch.battingFirst.players
                }
                val excProfileIds = opposingPlayers.mapNotNull { it.sourceProfileId }.toSet()
                val excNames = opposingPlayers.map { normalizePlayerName(it.name) }.toSet()
                SelectPlayerDialog(
                    title = title,
                    players = action.teamPlayers,
                    teamSectionLabel = "Select from team",
                    emptyTeamMessage = "No unused players left in the batting team",
                    savedPlayers = savedPlayers,
                    excludedProfileIds = excProfileIds,
                    excludedNames = excNames,
                    onPickFromSaved = { profile, isNew ->
                        if (isNew) onSavePrivatePlayer(profile)
                        val player = profile.toMatchPlayer()
                        matchViewModel.addPlayerToTeam(player, addToBattingTeam = true)
                        matchViewModel.selectNextBatter(player)
                    },
                    onPlayerSelected = { matchViewModel.selectNextBatter(it) },
                    onAllOut = { matchViewModel.endInningsAsAllOut() }
                )
            }
            is PendingAction.SelectBowler -> {
                // Dismissible bottom sheet: user can close it and use the rest of the app.
                // Scoring remains disabled (pendingAction != null) until a bowler is chosen.
                // An inline banner on the Score tab explains the blocked state.
                if (showSelectBowlerSheet) {
                    val opposingPlayers = when {
                        activeMatch == null -> emptyList()
                        console.inningsNumber == 1 -> activeMatch.battingFirst.players
                        else -> activeMatch.bowlingFirst.players
                    }
                    val excProfileIds = opposingPlayers.mapNotNull { it.sourceProfileId }.toSet()
                    val excNames = opposingPlayers.map { normalizePlayerName(it.name) }.toSet()
                    SelectBowlerBottomSheet(
                        availablePlayers = action.availablePlayers,
                        savedPlayers = savedPlayers,
                        excludedProfileIds = excProfileIds,
                        excludedNames = excNames,
                        onPlayerSelected = { matchViewModel.changeBowler(it) },
                        onPickFromSaved = { profile, isNew ->
                            if (isNew) onSavePrivatePlayer(profile)
                            val player = profile.toMatchPlayer()
                            matchViewModel.addPlayerToTeam(player, addToBattingTeam = false)
                            matchViewModel.changeBowler(player)
                        },
                        onDismiss = { showSelectBowlerSheet = false }
                    )
                }
            }
            null -> Unit
        }

        // --- Openers setup dialog ---
        if (setupDialogVisible && activeMatch != null && needsInningsSetup) {
            val battingTeam = if (console.inningsNumber == 1) activeMatch.battingFirst
                              else activeMatch.bowlingFirst
            val bowlingTeam = if (console.inningsNumber == 1) activeMatch.bowlingFirst
                              else activeMatch.battingFirst
            SetupOpenersBottomSheet(
                inningsNumber = console.inningsNumber,
                battingTeam = battingTeam,
                bowlingTeam = bowlingTeam,
                savedPlayers = savedPlayers,
                onConfirm = { striker, nonStriker, bowler ->
                    matchViewModel.setOpeners(striker, nonStriker, bowler)
                    setupDialogVisible = false
                },
                onDismiss = { setupDialogVisible = false },
                onAddPlayerToBattingTeam = { name ->
                    val profile = PlayerProfile(displayName = name)
                    onSavePrivatePlayer(profile)
                    matchViewModel.addPlayerToTeam(profile.toMatchPlayer(), addToBattingTeam = true)
                },
                onAddPlayerToBowlingTeam = { name ->
                    val profile = PlayerProfile(displayName = name)
                    onSavePrivatePlayer(profile)
                    matchViewModel.addPlayerToTeam(profile.toMatchPlayer(), addToBattingTeam = false)
                },
                onPickFromSaved = { profile, isNew, forBatting ->
                    if (isNew) onSavePrivatePlayer(profile)
                    matchViewModel.addPlayerToTeam(profile.toMatchPlayer(), addToBattingTeam = forBatting)
                }
            )
        }

        // --- Undo snackbar ---
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// =============================================================================
// Compact match header (shown above tabs)
// =============================================================================

@Composable
private fun CompactMatchHeader(
    match: Match?,
    state: MatchState,
    consoleState: ScoringConsoleState,
    onPreviewMatch: () -> Unit = {}
) {
    if (match == null) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // --- Score line ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = consoleState.battingTeamName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${state.runs}/${state.wickets}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "(${state.overs}.${state.balls})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    IconButton(
                        onClick = onPreviewMatch,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Preview Match",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            // --- Chase subtitle (2nd innings only) ---
            if (consoleState.phase == InningsPhase.SECOND_INNINGS) {
                val runsNeeded = (consoleState.target - state.runs).coerceAtLeast(0)
                val ballsBowled = state.overs * 6 + state.balls
                val totalBalls = match.overs * 6
                val ballsRemaining = (totalBalls - ballsBowled).coerceAtLeast(0)
                val chaseText = if (state.runs >= consoleState.target) {
                    "Target ${consoleState.target} reached"
                } else {
                    "Need $runsNeeded runs from $ballsRemaining balls"
                }
                Text(
                    text = chaseText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            } else if (consoleState.phase == InningsPhase.INNINGS_BREAK) {
                Text(
                    text = "Target ${consoleState.target}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
            // --- Current over/ball indicator (active innings only) ---
            if (consoleState.phase == InningsPhase.FIRST_INNINGS ||
                consoleState.phase == InningsPhase.SECOND_INNINGS
            ) {
                Text(
                    text = formatCurrentBallIndicator(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // --- Run rate row (active innings only) ---
                RunRateRow(
                    state = state,
                    consoleState = consoleState,
                    oversLimit = match.overs
                )
            }
        }
    }
}

/** Returns a compact string showing the current over and ball position, e.g. "Over 1 • Ball 3 of 6". */
private fun formatCurrentBallIndicator(matchState: MatchState): String {
    val currentOverNumber = matchState.overs + 1
    val currentBallNumber = matchState.balls + 1
    return "Over $currentOverNumber • Ball $currentBallNumber of 6"
}

// =============================================================================
// Run rate info row
// =============================================================================

@Composable
private fun RunRateRow(
    state: MatchState,
    consoleState: ScoringConsoleState,
    oversLimit: Int
) {
    val crr = ScorecardFormatter.formatRunRate(state.runs, state.overs, state.balls)

    val rrr: String? = if (consoleState.phase == InningsPhase.SECOND_INNINGS) {
        val runsNeeded = (consoleState.target - state.runs).coerceAtLeast(0)
        val ballsBowled = state.overs * 6 + state.balls
        val ballsRemaining = (oversLimit * 6 - ballsBowled).coerceAtLeast(0)
        ScorecardFormatter.formatRequiredRunRate(runsNeeded, ballsRemaining)
    } else {
        null
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "CRR $crr",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (rrr != null) {
            Text(
                text = "•",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = "RRR $rrr",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// =============================================================================
// Match header
// =============================================================================

@Composable
private fun MatchHeaderSection(match: Match, console: ScoringConsoleState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = match.displayTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${match.format.label.substringBefore(" (")} · ${match.overs} overs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.extraSmall
            ) {
                val inningsSuffix = if (console.inningsNumber == 1) "st" else "nd"
                Text(
                    text = "${console.inningsNumber}$inningsSuffix Innings",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "Bat: ${console.battingTeamName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Bowl: ${console.bowlingTeamName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

// =============================================================================
// Score display
// =============================================================================

@Composable
private fun ScoreDisplaySection(
    state: MatchState
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = state.teamAName,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = "${state.runs}/${state.wickets}",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Overs: ${state.overs}.${state.balls}",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

// =============================================================================
// Chase / target info
// =============================================================================

@Composable
private fun ChaseInfoSection(
    runsScored: Int,
    target: Int,
    overs: Int,
    balls: Int,
    oversLimit: Int
) {
    val runsNeeded = (target - runsScored).coerceAtLeast(0)
    val ballsBowled = overs * 6 + balls
    val totalBalls = oversLimit * 6
    val ballsRemaining = (totalBalls - ballsBowled).coerceAtLeast(0)

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            ChaseInfoItem(label = "Target", value = "$target")
            ChaseInfoItem(
                label = "Need",
                value = if (runsScored >= target) "Won!" else "$runsNeeded"
            )
            ChaseInfoItem(label = "Balls left", value = "$ballsRemaining")
        }
    }
}

@Composable
private fun ChaseInfoItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
    }
}

// =============================================================================
// Innings ball history ribbon
// =============================================================================

/**
 * A horizontally scrollable ribbon showing the full innings ball history grouped by over.
 *
 * Each over is rendered as a compact [OverBlock] containing a small over-number label and a
 * row of colour-coded ball chips. The ribbon automatically scrolls to the latest over as new
 * deliveries are recorded, while still allowing the scorer to scroll back to inspect earlier overs.
 */
@Composable
private fun BallHistoryRibbon(overs: List<OverSummary>) {
    val listState = rememberLazyListState()

    // Auto-scroll to the latest over block whenever the over list changes
    LaunchedEffect(overs.size, overs.lastOrNull()?.balls?.size) {
        if (overs.isNotEmpty()) {
            listState.animateScrollToItem(overs.lastIndex)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "This Innings",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (overs.isEmpty()) {
            Text(
                text = "No balls bowled yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        } else {
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(overs) { over ->
                    OverBlock(over)
                }
            }
        }
    }
}

/**
 * A compact block displaying a single over's number and its ball chips in a row.
 */
@Composable
private fun OverBlock(over: OverSummary) {
    Column(
        modifier = Modifier.wrapContentHeight(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "${over.overNumber}:",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            over.balls.forEach { indexedBall ->
                val label = OverSummaryCalculator.ballLabel(indexedBall.event)
                val bgColor = when {
                    label == "W" || label.endsWith("W") -> MaterialTheme.colorScheme.error
                    label == "4" -> MaterialTheme.colorScheme.secondaryContainer
                    // BoundarySixContainer is a cricket-domain semantic colour (dark green emphasis
                    // for a six) that has no equivalent standard M3 role in our scheme — it is used
                    // directly here and in BallTimelineScreen to keep the design intent explicit.
                    label == "6" -> BoundarySixContainer
                    label.startsWith("Wd") || label.startsWith("Nb") -> MaterialTheme.colorScheme.tertiaryContainer
                    label.startsWith("P") -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val textColor = when {
                    label == "W" || label.endsWith("W") -> MaterialTheme.colorScheme.onError
                    label == "4" -> MaterialTheme.colorScheme.onSecondaryContainer
                    label == "6" -> OnBoundarySixContainer
                    label.startsWith("Wd") || label.startsWith("Nb") -> MaterialTheme.colorScheme.onTertiaryContainer
                    label.startsWith("P") -> MaterialTheme.colorScheme.onSecondaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Surface(color = bgColor, shape = MaterialTheme.shapes.small) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = textColor,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

// =============================================================================
// Current players card
// =============================================================================

@Composable
private fun PlayersSection(console: ScoringConsoleState, onSwapStrike: () -> Unit) {
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "At the Crease",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                if (console.striker != null && console.nonStriker != null) {
                    IconButton(
                        onClick = onSwapStrike,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Swap strike",
                                modifier = Modifier.size(14.dp)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Striker
            console.strikerEntry?.let { BatterRow(entry = it, isStriker = true) }
                ?: console.striker?.let {
                    Text("${it.name} *", style = MaterialTheme.typography.bodySmall)
                }

            // Non-striker
            console.nonStrikerEntry?.let { BatterRow(entry = it, isStriker = false) }
                ?: console.nonStriker?.let {
                    Text(it.name, style = MaterialTheme.typography.bodySmall)
                }

            // Partnership
            if (console.striker != null && console.nonStriker != null) {
                Text(
                    text = "Partnership: ${console.currentPartnershipRuns} (${console.currentPartnershipBalls})",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            // Bowler
            console.currentBowlerEntry?.let { BowlerRow(entry = it) }
                ?: console.currentBowler?.let {
                    Text(it.name, style = MaterialTheme.typography.bodySmall)
                }
        }
    }
}

@Composable
private fun BatterRow(entry: BattingEntry, isStriker: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${entry.player.name}${if (isStriker) " *" else ""}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isStriker) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${entry.runs} (${entry.balls})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        if (entry.fours > 0 || entry.sixes > 0) {
            Text(
                text = "  4s:${entry.fours} 6s:${entry.sixes}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun BowlerRow(entry: BowlingEntry) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = entry.player.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            // Format: Overs-Maidens-Runs-Wickets  (e.g. 3.2-1-12-2)
            text = "${ScorecardFormatter.formatOvers(entry.overs, entry.balls)}-${entry.maidens}-${entry.runs}-${entry.wickets}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

// =============================================================================
// Scoring buttons
// =============================================================================

/**
 * A single styled scoring button with a comfortable minimum tap size.
 */
@Composable
private fun ScoringActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    colors: androidx.compose.material3.ButtonColors = ButtonDefaults.buttonColors(),
    fontWeight: FontWeight? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        modifier = modifier.defaultMinSize(minHeight = 52.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = fontWeight)
    }
}

/**
 * A labelled container section used to group scoring controls visually.
 */
@Composable
private fun ScoringControlsSection(
    label: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.padding(10.dp)) {
                content()
            }
        }
    }
}

/**
 * 2 × 3 grid of run buttons (0, 1, 2, 3, 4, 6).
 * Normal run buttons (0–3) use a light blue-grey neutral style.
 * Boundary buttons (4 and 6) use stronger accent colours to stand out.
 */
@Composable
private fun RunButtonsGrid(
    onEvent: (ScoreEvent) -> Unit,
    enabled: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Row 1: 0, 1, 2 — neutral light style
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(0, 1, 2).forEach { run ->
                ScoringActionButton(
                    text = "$run",
                    onClick = { onEvent(ScoreEvent.Run(run)) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NormalRunContainer,
                        contentColor = OnNormalRunContainer
                    )
                )
            }
        }
        // Row 2: 3 (neutral), 4 and 6 (boundary highlighted)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ScoringActionButton(
                text = "3",
                onClick = { onEvent(ScoreEvent.Run(3)) },
                enabled = enabled,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NormalRunContainer,
                    contentColor = OnNormalRunContainer
                )
            )
            ScoringActionButton(
                text = "4",
                onClick = { onEvent(ScoreEvent.Run(4)) },
                enabled = enabled,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BoundaryFourContainer,
                    contentColor = OnBoundaryFourContainer
                ),
                fontWeight = FontWeight.Bold
            )
            ScoringActionButton(
                text = "6",
                onClick = { onEvent(ScoreEvent.Run(6)) },
                enabled = enabled,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BoundarySixContainer,
                    contentColor = OnBoundarySixContainer
                ),
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

/**
 * Extras buttons grid: Wide, No Ball, Bye, Leg Bye, and Overthrows.
 * Styled as OutlinedButtons to appear secondary to run buttons.
 * Layout: 2 rows × 2 columns for the four standard extras, then a full-width Overthrows button.
 */
@Composable
private fun ExtrasButtonsGrid(
    onExtras: (ExtraType) -> Unit,
    onOverthrows: () -> Unit,
    enabled: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(ExtraType.WIDE, ExtraType.NO_BALL).forEach { type ->
                OutlinedButton(
                    onClick = { onExtras(type) },
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(type.label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(ExtraType.BYE, ExtraType.LEG_BYE).forEach { type ->
                OutlinedButton(
                    onClick = { onExtras(type) },
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(type.label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        // Overthrows — situational extra, grouped with extras for clarity
        OutlinedButton(
            onClick = onOverthrows,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
        ) {
            Text("Overthrows", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Scoring action row: Wicket, Undo, and "+5 Penalty" buttons.
 * Wicket uses errorContainer for a destructive appearance.
 * Undo uses secondary/onSecondary for a clearly visible filled button that is
 * secondary in visual weight (distinct from the red Wicket) and always clickable.
 * Penalty uses tertiaryContainer to signal a special, non-delivery action.
 */
@Composable
private fun ActionButtonsRow(
    onWicket: () -> Unit,
    onUndo: () -> Unit,
    onPenaltyRuns: () -> Unit,
    wicketEnabled: Boolean,
    penaltyEnabled: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onWicket,
                enabled = wicketEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 52.dp)
            ) {
                Text("Wicket", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = onUndo,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 52.dp)
            ) {
                Text("Undo", style = MaterialTheme.typography.labelLarge)
            }
        }
        // Penalty runs button — full width, distinct colour, available during active innings
        OutlinedButton(
            onClick = onPenaltyRuns,
            enabled = penaltyEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
        ) {
            Text("+5 Penalty Runs", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Full scoring control pad split into three labelled sections:
 * Runs → Extras → Actions (Wicket / Undo / Penalty).
 */
@Composable
private fun ScoringButtonsSection(
    onEvent: (ScoreEvent) -> Unit,
    onUndo: () -> Unit,
    onWicket: () -> Unit,
    onExtras: (ExtraType) -> Unit,
    onOverthrows: () -> Unit,
    onPenaltyRuns: () -> Unit,
    enabled: Boolean,
    penaltyEnabled: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScoringControlsSection(label = "Runs") {
            RunButtonsGrid(onEvent = onEvent, enabled = enabled)
        }
        ScoringControlsSection(label = "Extras") {
            ExtrasButtonsGrid(onExtras = onExtras, onOverthrows = onOverthrows, enabled = enabled)
        }
        ScoringControlsSection(label = "Actions") {
            ActionButtonsRow(
                onWicket = onWicket,
                onUndo = onUndo,
                onPenaltyRuns = onPenaltyRuns,
                wicketEnabled = enabled,
                penaltyEnabled = penaltyEnabled
            )
        }
    }
}

// =============================================================================
// Innings / match controls
// =============================================================================

@Composable
private fun InningsControlSection(
    console: ScoringConsoleState,
    onEndFirstInnings: () -> Unit,
    onEndMatch: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (console.phase == InningsPhase.FIRST_INNINGS) {
            OutlinedButton(onClick = onEndFirstInnings) {
                Text("End 1st Innings")
            }
        }
        if (console.phase == InningsPhase.SECOND_INNINGS) {
            OutlinedButton(
                onClick = onEndMatch,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("End Match")
            }
        }
    }
}

// =============================================================================
// Match complete
// =============================================================================

@Composable
private fun MatchCompleteSection(
    runsScored: Int,
    wickets: Int,
    console: ScoringConsoleState,
    battingTeamName: String,
    bowlingTeamName: String
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Match Complete",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (console.inningsNumber == 2) {
                val runsNeeded = console.target - runsScored
                val result = console.manualResultLabel ?: when {
                    runsScored >= console.target ->
                        "$battingTeamName won by ${10 - wickets} wickets!"
                    wickets >= 10 ->
                        // target = firstInningsRuns + 1, so margin = target - 1 - runsScored = runsNeeded - 1
                        "$bowlingTeamName won by ${runsNeeded - 1} runs!"
                    else -> "Match ended"
                }
                Text(result, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    text = "1st innings: ${console.firstInningsRuns}/${console.firstInningsWickets}" +
                            "   |   2nd innings: $runsScored/$wickets",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            } else if (console.manualResultLabel != null) {
                // Match ended manually during/before the 2nd innings (e.g. abandoned)
                Text(
                    console.manualResultLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// =============================================================================
// Player-selection dialog (wicket / bowler change)
// =============================================================================

/**
 * Non-dismissible player list dialog.
 *
 * @param teamSectionLabel When non-null, a section header is displayed above the player list
 *   to visually identify that these players are from the batting/bowling team. This makes the
 *   team-player path visually primary.
 * @param emptyTeamMessage When non-null and [players] is empty, this message is shown instead
 *   of the player list, explaining why there are no team players to choose from.
 * @param savedPlayers When non-empty and [onPickFromSaved] is non-null, an "Add Player"
 *   button is shown so the scorer can search, pick, or create a player via [PlayerPickerDialog].
 * @param onPickFromSaved Optional callback triggered when a player is chosen (or created) via
 *   [PlayerPickerDialog]. Receives the [PlayerProfile] and an [isNew] flag so the caller can
 *   persist the profile if it was freshly created.
 * @param onAllOut Optional callback: when non-null, a "No more players / All out" button is
 *   shown so the scorer can end the innings immediately without selecting a batter.
 */
@Composable
private fun SelectPlayerDialog(
    title: String,
    players: List<Player>,
    onPlayerSelected: (Player) -> Unit,
    teamSectionLabel: String? = null,
    emptyTeamMessage: String? = null,
    savedPlayers: List<PlayerProfile> = emptyList(),
    excludedProfileIds: Set<String> = emptySet(),
    excludedNames: Set<String> = emptySet(),
    onPickFromSaved: ((profile: PlayerProfile, isNew: Boolean) -> Unit)? = null,
    onAllOut: (() -> Unit)? = null
) {
    var showSavedPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { /* must select */ },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // --- Team player list (primary path) ---
                if (teamSectionLabel != null) {
                    Text(
                        text = teamSectionLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                if (players.isEmpty()) {
                    if (emptyTeamMessage != null) {
                        Text(
                            text = emptyTeamMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    } else {
                        Text("No available players to select")
                    }
                }
                players.forEach { player ->
                    TextButton(
                        onClick = { onPlayerSelected(player) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = player.name,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                // --- Add Player (opens unified PlayerPickerDialog) ---
                if (onPickFromSaved != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    OutlinedButton(
                        onClick = { showSavedPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Add Player")
                    }
                }
                if (onAllOut != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Button(
                        onClick = onAllOut,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("No more players / All out")
                    }
                }
            }
        },
        confirmButton = {}
    )

    // PlayerPickerDialog handles both picking saved players and creating new ones
    if (showSavedPicker && onPickFromSaved != null) {
        PlayerPickerDialog(
            savedPlayers = savedPlayers,
            excludedProfileIds = excludedProfileIds,
            excludedNames = excludedNames,
            onDismiss = { showSavedPicker = false },
            onSelect = { profile ->
                showSavedPicker = false
                onPickFromSaved(profile, false)
            },
            onCreateAndSelect = { profile ->
                showSavedPicker = false
                onPickFromSaved(profile, true)
            }
        )
    }
}

// =============================================================================
// Wicket details dialog
// =============================================================================

/**
 * Dialog shown when the scorer taps the W (Wicket) button.
 *
 * Lets the scorer specify:
 * - Who got out (striker or non-striker)
 * - How they were dismissed (dismissal type)
 * - Optional fielder involved (catcher, wicketkeeper, or run-out fielder)
 */
@Composable
internal fun WicketDetailsDialog(
    striker: Player?,
    nonStriker: Player?,
    bowlingTeamPlayers: List<Player>,
    currentBowler: Player?,
    onConfirm: (DismissalDetail, Int) -> Unit,
    onDismiss: () -> Unit
) {
    // Default to striker out (the most common case)
    var batterOut by remember { mutableStateOf(striker ?: nonStriker) }
    var selectedType by remember { mutableStateOf(DismissalType.BOWLED) }
    var selectedFielder by remember { mutableStateOf<Player?>(null) }
    var selectedFielder2 by remember { mutableStateOf<Player?>(null) }
    var showSecondFielder by remember { mutableStateOf(false) }
    var runOutRunsText by remember { mutableStateOf("0") }

    // Fielder is relevant for Caught, Stumped, and Run Out dismissals.
    val requiresFielder = selectedType in listOf(
        DismissalType.CAUGHT, DismissalType.STUMPED, DismissalType.RUN_OUT
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wicket Details") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // --- Who got out ---
                Text("Who got out?", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (striker != null) {
                        FilterChip(
                            selected = batterOut?.id == striker.id,
                            onClick = { batterOut = striker },
                            label = { Text("${striker.name} (striker)") }
                        )
                    }
                    if (nonStriker != null) {
                        FilterChip(
                            selected = batterOut?.id == nonStriker.id,
                            onClick = { batterOut = nonStriker },
                            label = { Text("${nonStriker.name} (non-striker)") }
                        )
                    }
                }

                HorizontalDivider()

                // --- Dismissal type ---
                Text("How?", style = MaterialTheme.typography.labelMedium)
                // Two rows of chips for the 6 dismissal types
                val types = DismissalType.entries
                val onTypeSelected: (DismissalType) -> Unit = { type ->
                    selectedType = type
                    selectedFielder = null
                    selectedFielder2 = null
                    showSecondFielder = false
                    runOutRunsText = "0"
                    if (type == DismissalType.RUN_OUT) {
                        Log.d("WicketFlow", "Run-out selected")
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        types.take(3).forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { onTypeSelected(type) },
                                label = { Text(type.label) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        types.drop(3).forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { onTypeSelected(type) },
                                label = { Text(type.label) }
                            )
                        }
                    }
                }

                // --- Fielder selection (for Caught / Stumped / Run Out) ---
                if (requiresFielder) {
                    HorizontalDivider()
                    val fielderLabel = when (selectedType) {
                        DismissalType.CAUGHT -> "Catcher"
                        DismissalType.STUMPED -> "Wicketkeeper"
                        DismissalType.RUN_OUT -> "Fielder 1"
                        else -> "Fielder"
                    }
                    Text(fielderLabel, style = MaterialTheme.typography.labelMedium)
                    if (bowlingTeamPlayers.isEmpty()) {
                        Text(
                            "No fielding team players registered.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    } else {
                        bowlingTeamPlayers.forEach { player ->
                            FilterChip(
                                selected = selectedFielder?.id == player.id,
                                onClick = {
                                    selectedFielder = if (selectedFielder?.id == player.id) null else player
                                    // If first fielder cleared, also clear second
                                    if (selectedFielder == null) {
                                        selectedFielder2 = null
                                        showSecondFielder = false
                                    }
                                },
                                label = { Text(player.name) }
                            )
                        }
                    }

                    // Second fielder (Run Out only)
                    if (selectedType == DismissalType.RUN_OUT) {
                        if (!showSecondFielder) {
                            TextButton(
                                onClick = { showSecondFielder = true },
                                enabled = selectedFielder != null
                            ) {
                                Text("+ Add second fielder (optional)")
                            }
                        } else {
                            Text("Fielder 2 (optional)", style = MaterialTheme.typography.labelMedium)
                            if (bowlingTeamPlayers.isEmpty()) {
                                Text(
                                    "No fielding team players registered.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            } else {
                                bowlingTeamPlayers
                                    .filter { it.id != selectedFielder?.id }
                                    .forEach { player ->
                                        FilterChip(
                                            selected = selectedFielder2?.id == player.id,
                                            onClick = {
                                                selectedFielder2 =
                                                    if (selectedFielder2?.id == player.id) null else player
                                            },
                                            label = { Text(player.name) }
                                        )
                                    }
                                if (selectedFielder2 != null) {
                                    TextButton(onClick = {
                                        selectedFielder2 = null
                                        showSecondFielder = false
                                    }) {
                                        Text("Remove second fielder")
                                    }
                                }
                            }
                        }
                    }
                }

                // --- Runs completed (Run Out only) ---
                if (selectedType == DismissalType.RUN_OUT) {
                    HorizontalDivider()
                    Text("How many runs were completed?", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (0..6).forEach { runs ->
                            FilterChip(
                                selected = runOutRunsText == runs.toString(),
                                onClick = { runOutRunsText = runs.toString() },
                                label = { Text("$runs") }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val runsCompleted = runOutRunsText.toIntOrNull()?.coerceIn(0, 6) ?: 0
            Button(
                onClick = {
                    val out = batterOut ?: return@Button
                    val fieldersList = when {
                        !requiresFielder -> emptyList()
                        selectedType == DismissalType.RUN_OUT ->
                            listOfNotNull(selectedFielder, selectedFielder2)
                        else -> listOfNotNull(selectedFielder)
                    }
                    if (selectedType == DismissalType.RUN_OUT) {
                        Log.d("WicketFlow", "Runs completed: $runsCompleted")
                    }
                    onConfirm(
                        DismissalDetail(
                            batter = out,
                            dismissalType = selectedType,
                            fielders = fieldersList,
                            bowler = currentBowler
                        ),
                        runsCompleted
                    )
                },
                enabled = batterOut != null
            ) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// =============================================================================
// Extras entry dialog
// =============================================================================

/**
 * Dialog shown when the scorer taps one of the extras buttons (Wide, No Ball, Bye, Leg Bye).
 *
 * Lets the scorer specify:
 * - Extra type (pre-filled from the button that was tapped, but changeable)
 * - Total runs on the delivery (defaults to 1 for all types)
 * - Optional run-out wicket on the same delivery
 *   - Which batter was run out (striker or non-striker)
 *   - Optional fielder involved
 *
 * Only Run Out is allowed as a wicket mode for extras, matching real-world cricket rules.
 */
@Composable
internal fun ExtrasEntryDialog(
    initialType: ExtraType,
    striker: Player?,
    nonStriker: Player?,
    bowlingTeamPlayers: List<Player>,
    onConfirm: (BallEvent) -> Unit,
    onDismiss: () -> Unit
) {
    var extraType by remember { mutableStateOf(initialType) }
    var selectedRuns by remember { mutableStateOf(1) }
    var customRunsText by remember { mutableStateOf("") }
    var useCustomRuns by remember { mutableStateOf(false) }
    var hasWicket by remember { mutableStateOf(false) }
    var batterOut by remember { mutableStateOf(striker ?: nonStriker) }
    var selectedFielder by remember { mutableStateOf<Player?>(null) }
    // Overthrow state — only used when extraType is BYE or LEG_BYE
    var hasOverthrows by remember { mutableStateOf(false) }
    var overthrowRuns by remember { mutableStateOf(1) }
    var overthrowCustomText by remember { mutableStateOf("") }
    var overthrowUseCustom by remember { mutableStateOf(false) }

    // Reset wicket state whenever the extra type changes
    LaunchedEffect(extraType) {
        hasWicket = false
        batterOut = striker ?: nonStriker
        selectedFielder = null
        hasOverthrows = false
        overthrowRuns = 1
        overthrowCustomText = ""
        overthrowUseCustom = false
    }

    val totalRuns = if (useCustomRuns) customRunsText.toIntOrNull()?.coerceAtLeast(1) ?: selectedRuns else selectedRuns
    val finalOverthrowRuns = if (overthrowUseCustom)
        overthrowCustomText.toIntOrNull()?.coerceAtLeast(1) ?: overthrowRuns
    else
        overthrowRuns
    val overthrowsApply = hasOverthrows &&
            (extraType == ExtraType.BYE || extraType == ExtraType.LEG_BYE)

    val isValid = !hasWicket || batterOut != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Extras Entry") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // --- Extra type selector ---
                Text("Extra type", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExtraType.entries.forEach { type ->
                        FilterChip(
                            selected = extraType == type,
                            onClick = { extraType = type },
                            label = { Text(type.label) }
                        )
                    }
                }

                HorizontalDivider()

                // --- Runs selector ---
                Text("Runs on delivery", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 2, 3, 4).forEach { runs ->
                        FilterChip(
                            selected = !useCustomRuns && selectedRuns == runs,
                            onClick = { selectedRuns = runs; useCustomRuns = false },
                            label = { Text("$runs") }
                        )
                    }
                    FilterChip(
                        selected = useCustomRuns,
                        onClick = { useCustomRuns = true },
                        label = { Text("5+") }
                    )
                }
                if (useCustomRuns) {
                    OutlinedTextField(
                        value = customRunsText,
                        onValueChange = { customRunsText = it.filter { char -> char.isDigit() } },
                        label = { Text("Enter runs") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider()

                // --- Overthrow section (Bye / Leg Bye only) ---
                if (extraType == ExtraType.BYE || extraType == ExtraType.LEG_BYE) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = hasOverthrows,
                            onCheckedChange = { checked ->
                                hasOverthrows = checked
                                if (!checked) {
                                    overthrowRuns = 1
                                    overthrowCustomText = ""
                                    overthrowUseCustom = false
                                }
                            }
                        )
                        Text("Overthrows happened", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (hasOverthrows) {
                        Text("Overthrow runs", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1, 2, 3, 4).forEach { runs ->
                                FilterChip(
                                    selected = !overthrowUseCustom && overthrowRuns == runs,
                                    onClick = { overthrowRuns = runs; overthrowUseCustom = false },
                                    label = { Text("$runs") }
                                )
                            }
                            FilterChip(
                                selected = overthrowUseCustom,
                                onClick = { overthrowUseCustom = true },
                                label = { Text("5+") }
                            )
                        }
                        if (overthrowUseCustom) {
                            OutlinedTextField(
                                value = overthrowCustomText,
                                onValueChange = { overthrowCustomText = it.filter { char -> char.isDigit() } },
                                label = { Text("Overthrow runs") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Text(
                            "Total: ${totalRuns + finalOverthrowRuns} (${extraType.label.lowercase()}: $totalRuns + overthrow: $finalOverthrowRuns)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    HorizontalDivider()
                }

                // --- Wicket toggle ---
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = hasWicket,
                        onCheckedChange = { checked ->
                            hasWicket = checked
                            if (!checked) { batterOut = striker ?: nonStriker; selectedFielder = null }
                        }
                    )
                    Text("Wicket on this ball (Run Out only)", style = MaterialTheme.typography.bodyMedium)
                }

                // --- Wicket detail section (only when hasWicket is true) ---
                if (hasWicket) {
                    HorizontalDivider()
                    Text("Who was run out?", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (striker != null) {
                            FilterChip(
                                selected = batterOut?.id == striker.id,
                                onClick = { batterOut = striker },
                                label = { Text("${striker.name} (striker)") }
                            )
                        }
                        if (nonStriker != null) {
                            FilterChip(
                                selected = batterOut?.id == nonStriker.id,
                                onClick = { batterOut = nonStriker },
                                label = { Text("${nonStriker.name} (non-striker)") }
                            )
                        }
                    }

                    Text("Fielder (optional)", style = MaterialTheme.typography.labelMedium)
                    if (bowlingTeamPlayers.isEmpty()) {
                        Text(
                            "No fielding team players registered.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            bowlingTeamPlayers.forEach { player ->
                                FilterChip(
                                    selected = selectedFielder?.id == player.id,
                                    onClick = {
                                        selectedFielder = if (selectedFielder?.id == player.id) null else player
                                    },
                                    label = { Text(player.name) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val overthrowsToAdd = if (overthrowsApply) finalOverthrowRuns else 0
                    val ballEvent = buildExtrasEvent(extraType, totalRuns, overthrowsToAdd, hasWicket, batterOut, selectedFielder)
                    onConfirm(ballEvent)
                },
                enabled = isValid
            ) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Constructs a [BallEvent] for an extras delivery.
 *
 * Run mapping:
 * - **Wide**: all runs go to wides (including the 1-run penalty). User sees "total runs".
 * - **No Ball**: 1 run is the no-ball penalty (extras); remaining runs are credited off bat.
 * - **Bye / Leg Bye**: all runs (including any overthrow runs) go directly to byes / leg-byes.
 *
 * [overthrowRuns] is folded into the byes/legByes total for Bye and Leg Bye deliveries.
 * A wicket on an extras delivery is always a **Run Out** and does NOT credit the bowler.
 */
private fun buildExtrasEvent(
    type: ExtraType,
    runs: Int,
    overthrowRuns: Int = 0,
    hasWicket: Boolean,
    batterOut: Player?,
    fielder: Player?
): BallEvent {
    val dismissal: DismissalDetail? = if (hasWicket && batterOut != null) {
        DismissalDetail(
            batter = batterOut,
            dismissalType = DismissalType.RUN_OUT,
            fielders = listOfNotNull(fielder),
            bowler = null
        )
    } else null

    return when (type) {
        ExtraType.WIDE -> BallEvent(
            extras = ExtrasBreakdown(wides = runs),
            wicket = hasWicket,
            dismissalDetail = dismissal,
            countsAsBall = false
        )
        ExtraType.NO_BALL -> BallEvent(
            runsOffBat = maxOf(0, runs - 1),
            extras = ExtrasBreakdown(noBalls = 1),
            wicket = hasWicket,
            dismissalDetail = dismissal,
            countsAsBall = false
        )
        ExtraType.BYE -> BallEvent(
            extras = ExtrasBreakdown(byes = runs + overthrowRuns),
            wicket = hasWicket,
            dismissalDetail = dismissal,
            countsAsBall = true
        )
        ExtraType.LEG_BYE -> BallEvent(
            extras = ExtrasBreakdown(legByes = runs + overthrowRuns),
            wicket = hasWicket,
            dismissalDetail = dismissal,
            countsAsBall = true
        )
    }
}

// =============================================================================
// Wide / No Ball dedicated extras entry dialog
// =============================================================================

/**
 * Quick-selection dialog for Wide and No Ball deliveries.
 *
 * Shows a grid of buttons (Wd / Nb through Wd+6 / Nb+6). Tapping a button
 * immediately builds and dispatches the [BallEvent] — no confirmation step.
 *
 * Wide:  extras.wides = 1 + additionalRuns; countsAsBall = false.
 * No Ball: extras.noBalls = 1; runsOffBat = additionalRuns; countsAsBall = false.
 */
@Composable
internal fun WideNoBallEntryDialog(
    type: ExtraType,
    onConfirm: (BallEvent) -> Unit,
    onDismiss: () -> Unit
) {
    val prefix = if (type == ExtraType.WIDE) "Wd" else "Nb"
    // 0 additional runs = base extra only; 1..6 = base + additional runs
    val options = (0..6).toList()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(type.label) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.wrapContentHeight()
            ) {
                items(options) { additionalRuns ->
                    val label = if (additionalRuns == 0) prefix else "$prefix +$additionalRuns"
                    Button(
                        onClick = {
                            onConfirm(buildWideNoBallEvent(type, additionalRuns))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Constructs a [BallEvent] for a Wide or No Ball delivery.
 *
 * - **Wide**: wides = 1 + additionalRuns; runsOffBat = 0; countsAsBall = false.
 * - **No Ball**: noBalls = 1; runsOffBat = additionalRuns; countsAsBall = false.
 *
 * [additionalRuns] is the runs taken by running AFTER the automatic +1 extra.
 */
private fun buildWideNoBallEvent(
    type: ExtraType,
    additionalRuns: Int
): BallEvent {
    return when (type) {
        ExtraType.WIDE -> BallEvent(
            extras = ExtrasBreakdown(wides = 1 + additionalRuns),
            wicket = false,
            dismissalDetail = null,
            countsAsBall = false
        )
        ExtraType.NO_BALL -> BallEvent(
            runsOffBat = additionalRuns,
            extras = ExtrasBreakdown(noBalls = 1),
            wicket = false,
            dismissalDetail = null,
            countsAsBall = false
        )
        else -> error("buildWideNoBallEvent called with non-wide/no-ball type: $type")
    }
}

// =============================================================================
// Bye / Leg Bye dedicated entry dialog
// =============================================================================

/**
 * Quick-selection dialog for Bye and Leg Bye deliveries.
 *
 * Shows a 3-column grid of buttons (B+1 … B+6 or LB+1 … LB+6). Tapping a button
 * immediately builds and dispatches the [BallEvent] — no confirmation step.
 *
 * Bye:     extras.byes = runs;    countsAsBall = true.
 * Leg Bye: extras.legByes = runs; countsAsBall = true.
 */
@Composable
internal fun ByeLegByeEntryDialog(
    type: ExtraType,
    onConfirm: (BallEvent) -> Unit,
    onDismiss: () -> Unit
) {
    val prefix = if (type == ExtraType.BYE) "B" else "LB"
    val title = if (type == ExtraType.BYE) "Bye Runs" else "Leg Bye Runs"
    val options = (1..6).toList()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.wrapContentHeight()
            ) {
                items(options) { runs ->
                    Button(
                        onClick = {
                            onConfirm(buildByeLegByeEvent(type, runs))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("$prefix+$runs", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Constructs a [BallEvent] for a Bye or Leg Bye delivery.
 *
 * - **Bye**: all runs go to [ExtrasBreakdown.byes]; counts as a legal ball.
 * - **Leg Bye**: all runs go to [ExtrasBreakdown.legByes]; counts as a legal ball.
 */
private fun buildByeLegByeEvent(type: ExtraType, runs: Int): BallEvent {
    return when (type) {
        ExtraType.BYE -> BallEvent(
            extras = ExtrasBreakdown(byes = runs),
            wicket = false,
            dismissalDetail = null,
            countsAsBall = true
        )
        ExtraType.LEG_BYE -> BallEvent(
            extras = ExtrasBreakdown(legByes = runs),
            wicket = false,
            dismissalDetail = null,
            countsAsBall = true
        )
        else -> error("buildByeLegByeEvent called with non-bye/leg-bye type: $type")
    }
}

// =============================================================================
// Overthrow delivery dialog
// =============================================================================

/**
 * Dialog for recording a delivery where overthrows occurred.
 *
 * The scorer specifies:
 * - Base runs off bat (0, 1, 2, 3, 4, 6)
 * - Additional overthrow runs (1, 2, 3, 4, 5+)
 *
 * The final [BallEvent] folds both into [BallEvent.runsOffBat]:
 *   runsOffBat = baseRuns + overthrowRuns
 */
@Composable
internal fun OverthrowRunDialog(
    onConfirm: (BallEvent) -> Unit,
    onDismiss: () -> Unit
) {
    var baseRuns by remember { mutableStateOf(0) }
    var overthrowRuns by remember { mutableStateOf(1) }
    var overthrowCustomText by remember { mutableStateOf("") }
    var overthrowUseCustom by remember { mutableStateOf(false) }

    val finalOverthrowRuns = if (overthrowUseCustom)
        overthrowCustomText.toIntOrNull()?.coerceAtLeast(1) ?: overthrowRuns
    else
        overthrowRuns

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Runs with Overthrows") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // --- Base runs (off bat) ---
                Text("Runs off bat", style = MaterialTheme.typography.labelMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(0, 1, 2, 3, 4, 6).forEach { runs ->
                        FilterChip(
                            selected = baseRuns == runs,
                            onClick = { baseRuns = runs },
                            label = { Text("$runs") }
                        )
                    }
                }

                HorizontalDivider()

                // --- Overthrow runs ---
                Text("Overthrow runs", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 2, 3, 4).forEach { runs ->
                        FilterChip(
                            selected = !overthrowUseCustom && overthrowRuns == runs,
                            onClick = { overthrowRuns = runs; overthrowUseCustom = false },
                            label = { Text("$runs") }
                        )
                    }
                    FilterChip(
                        selected = overthrowUseCustom,
                        onClick = { overthrowUseCustom = true },
                        label = { Text("5+") }
                    )
                }
                if (overthrowUseCustom) {
                    OutlinedTextField(
                        value = overthrowCustomText,
                        onValueChange = { overthrowCustomText = it.filter { char -> char.isDigit() } },
                        label = { Text("Overthrow runs") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider()

                // --- Summary ---
                Text(
                    "Total runs: ${baseRuns + finalOverthrowRuns} (bat: $baseRuns + overthrow: $finalOverthrowRuns)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        BallEvent(
                            runsOffBat = baseRuns + finalOverthrowRuns,
                            countsAsBall = true
                        )
                    )
                }
            ) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// =============================================================================
// Penalty runs dialog
// =============================================================================

/**
 * Dialog shown when the scorer taps "+5 Penalty Runs".
 *
 * Allows the scorer to award 5 penalty runs (the standard cricket amount) or enter
 * a custom number of penalty runs.  The confirmed runs are dispatched to the ViewModel
 * as a penalty event that credits the batting team's total without affecting ball count
 * or strike.
 */
@Composable
internal fun PenaltyRunsDialog(
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedRuns by remember { mutableStateOf(5) }
    var useCustom by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }

    // When custom mode is active the input must be a valid positive integer;
    // null means the text is empty or non-numeric, which disables the Confirm button.
    val customRuns: Int? = if (useCustom) customText.toIntOrNull()?.takeIf { it >= 1 } else null
    val finalRuns: Int? = if (useCustom) customRuns else selectedRuns
    val isValid = finalRuns != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Award Penalty Runs") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Penalty runs are credited to the batting team without affecting the ball count or strike.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("Runs", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5).forEach { runs ->
                        FilterChip(
                            selected = !useCustom && selectedRuns == runs,
                            onClick = { selectedRuns = runs; useCustom = false },
                            label = { Text("$runs") }
                        )
                    }
                    FilterChip(
                        selected = useCustom,
                        onClick = { useCustom = true },
                        label = { Text("Custom") }
                    )
                }
                if (useCustom) {
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it.filter { c -> c.isDigit() } },
                        label = { Text("Penalty runs") },
                        isError = customText.isNotEmpty() && customRuns == null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (isValid) {
                    Text(
                        "Penalty runs: $finalRuns",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { finalRuns?.let { onConfirm(it) } },
                enabled = isValid
            ) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// =============================================================================
// Add player during an active match
// =============================================================================

/**
 * Dialog shown when the scorer taps "+ Add player to team" during an active match.
 *
 * Lets the scorer choose which team to add to, then opens [PlayerPickerDialog] to
 * search / pick an existing saved player or create a new one on the fly.
 */
@Composable
private fun AddPlayerToMatchDialog(
    battingTeamName: String,
    bowlingTeamName: String,
    battingTeamPlayers: List<Player> = emptyList(),
    bowlingTeamPlayers: List<Player> = emptyList(),
    savedPlayers: List<PlayerProfile> = emptyList(),
    onDismiss: () -> Unit,
    onPickFromSaved: (profile: PlayerProfile, isNew: Boolean, addToBattingTeam: Boolean) -> Unit
) {
    var addToBatting by remember { mutableStateOf(true) }
    var showPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Player") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Add to team", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = addToBatting,
                        onClick = { addToBatting = true },
                        label = { Text(battingTeamName) }
                    )
                    FilterChip(
                        selected = !addToBatting,
                        onClick = { addToBatting = false },
                        label = { Text(bowlingTeamName) }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { showPicker = true }) { Text("Pick / Add Player") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showPicker) {
        // Exclude players already in the opposing team so invalid options are hidden.
        val opposingPlayers = if (addToBatting) bowlingTeamPlayers else battingTeamPlayers
        val excProfileIds = opposingPlayers.mapNotNull { it.sourceProfileId }.toSet()
        val excNames = opposingPlayers.map { normalizePlayerName(it.name) }.toSet()
        PlayerPickerDialog(
            savedPlayers = savedPlayers,
            excludedProfileIds = excProfileIds,
            excludedNames = excNames,
            onDismiss = { showPicker = false },
            onSelect = { profile ->
                onPickFromSaved(profile, false, addToBatting)
            },
            onCreateAndSelect = { profile ->
                onPickFromSaved(profile, true, addToBatting)
            }
        )
    }
}

// =============================================================================
// Opening batters + bowler setup bottom sheet
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupOpenersBottomSheet(
    inningsNumber: Int,
    battingTeam: Team,
    bowlingTeam: Team,
    savedPlayers: List<PlayerProfile> = emptyList(),
    onConfirm: (striker: Player, nonStriker: Player, bowler: Player) -> Unit,
    onDismiss: () -> Unit,
    onAddPlayerToBattingTeam: (name: String) -> Unit,
    onAddPlayerToBowlingTeam: (name: String) -> Unit,
    /**
     * Called when a player is chosen or created via [PlayerPickerDialog] in the add-player rows.
     * [isNew] is true when the player was created inline (caller should persist the profile).
     * [forBatting] identifies which team to add the player to.
     */
    onPickFromSaved: ((profile: PlayerProfile, isNew: Boolean, forBatting: Boolean) -> Unit)? = null
) {
    // Eligible player lists: filter out players whose identity already appears in the
    // opposing team, so the dropdowns only show valid options.
    val eligibleBatters = remember(battingTeam.players, bowlingTeam.players) {
        battingTeam.players.filter { p -> bowlingTeam.players.none { it.sameIdentityAs(p) } }
    }
    val eligibleBowlers = remember(bowlingTeam.players, battingTeam.players) {
        bowlingTeam.players.filter { p -> battingTeam.players.none { it.sameIdentityAs(p) } }
    }

    // Auto-select defaults on first composition; when the roster changes later,
    // keep the current selection if it is still valid rather than resetting.
    var striker by remember { mutableStateOf(eligibleBatters.getOrNull(0)) }
    var nonStriker by remember { mutableStateOf(eligibleBatters.getOrNull(1)) }
    var bowler by remember { mutableStateOf(eligibleBowlers.getOrNull(0)) }

    // When batting team roster changes (e.g. a new player was added mid-setup),
    // refresh the selection only if the previously chosen player is no longer present.
    LaunchedEffect(eligibleBatters) {
        if (striker == null || eligibleBatters.none { it.id == striker?.id }) {
            striker = eligibleBatters.firstOrNull { it.id != nonStriker?.id }
        }
        if (nonStriker == null || eligibleBatters.none { it.id == nonStriker?.id }) {
            nonStriker = eligibleBatters.firstOrNull { it.id != striker?.id }
        }
    }
    LaunchedEffect(eligibleBowlers) {
        if (bowler == null || eligibleBowlers.none { it.id == bowler?.id }) {
            bowler = eligibleBowlers.firstOrNull()
        }
    }
    // pickerForBatting: true = show player picker for batting team, false = bowling team
    var pickerForBatting by remember { mutableStateOf<Boolean?>(null) }

    val inningsLabel = when (inningsNumber) {
        1 -> "Start 1st Innings"
        2 -> "Start 2nd Innings"
        else -> "Start Innings"
    }
    val needsMoreBatters = eligibleBatters.size < 2
    val needsMoreBowlers = eligibleBowlers.isEmpty()
    val canStart = striker != null && nonStriker != null && bowler != null

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Title
            Text(
                text = inningsLabel,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // ── Batting Team ──────────────────────────────────────────────
            Text(
                text = "Batting Team",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = battingTeam.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // ── Batters ───────────────────────────────────────────────────
            Text(
                text = "Batters",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            if (needsMoreBatters) {
                Text(
                    text = "You need at least 2 batters to start the innings.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            PlayerDropdown(
                label = "Striker",
                players = eligibleBatters,
                selected = striker,
                onSelected = { newStriker ->
                    if (newStriker?.id == nonStriker?.id) {
                        // Auto-swap: move the previous striker into the non-striker slot
                        nonStriker = striker
                        striker = newStriker
                    } else {
                        striker = newStriker
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = {
                    val temp = striker
                    striker = nonStriker
                    nonStriker = temp
                },
                enabled = striker != null && nonStriker != null,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("⇅ Swap batters")
            }
            Spacer(modifier = Modifier.height(8.dp))
            PlayerDropdown(
                label = "Non-striker",
                players = eligibleBatters,
                selected = nonStriker,
                onSelected = { newNonStriker ->
                    if (newNonStriker?.id == striker?.id) {
                        // Auto-swap: move the previous non-striker into the striker slot
                        striker = nonStriker
                        nonStriker = newNonStriker
                    } else {
                        nonStriker = newNonStriker
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Add Batter button
            OutlinedButton(
                onClick = { pickerForBatting = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("+ Add Batter") }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(20.dp))

            // ── Bowling Team ──────────────────────────────────────────────
            Text(
                text = "Bowling Team",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = bowlingTeam.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // ── Bowler ────────────────────────────────────────────────────
            Text(
                text = "Opening Bowler",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            if (needsMoreBowlers) {
                Text(
                    text = "You need at least 1 bowler to start the innings.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            PlayerDropdown(
                label = "Opening bowler",
                players = eligibleBowlers,
                selected = bowler,
                onSelected = { bowler = it }
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Add Bowler button
            OutlinedButton(
                onClick = { pickerForBatting = false },
                modifier = Modifier.fillMaxWidth()
            ) { Text("+ Add Bowler") }

            Spacer(modifier = Modifier.height(24.dp))

            // ── CTA ───────────────────────────────────────────────────────
            Button(
                onClick = {
                    val s = striker; val ns = nonStriker; val b = bowler
                    if (s != null && ns != null && b != null) onConfirm(s, ns, b)
                },
                enabled = canStart,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(inningsLabel)
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Later")
            }
        }
    }

    // ── Add-player dialog (opened when + Add Batter / + Add Bowler is tapped) ──
    val pickerTarget = pickerForBatting
    if (pickerTarget != null) {
        // Exclude players already in the opposing team from the picker.
        val opposingPlayers = if (pickerTarget) bowlingTeam.players else battingTeam.players
        val excProfileIds = opposingPlayers.mapNotNull { it.sourceProfileId }.toSet()
        val excNames = opposingPlayers.map { normalizePlayerName(it.name) }.toSet()
        PlayerPickerDialog(
            savedPlayers = savedPlayers,
            excludedProfileIds = excProfileIds,
            excludedNames = excNames,
            onDismiss = { pickerForBatting = null },
            onSelect = { profile ->
                pickerForBatting = null
                if (onPickFromSaved != null) {
                    onPickFromSaved(profile, false, pickerTarget)
                } else {
                    if (pickerTarget) onAddPlayerToBattingTeam(profile.displayName)
                    else onAddPlayerToBowlingTeam(profile.displayName)
                }
            },
            onCreateAndSelect = { profile ->
                pickerForBatting = null
                if (onPickFromSaved != null) {
                    onPickFromSaved(profile, true, pickerTarget)
                } else {
                    if (pickerTarget) onAddPlayerToBattingTeam(profile.displayName)
                    else onAddPlayerToBowlingTeam(profile.displayName)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerDropdown(
    label: String,
    players: List<Player>,
    selected: Player?,
    onSelected: (Player) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.name ?: "— select —",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            players.forEach { player ->
                DropdownMenuItem(
                    text = { Text(player.name) },
                    onClick = { onSelected(player); expanded = false }
                )
            }
        }
    }
}

// =============================================================================
// Innings break section
// =============================================================================

@Composable
private fun InningsBreakSection(
    battingFirstTeam: String,
    firstInningsRuns: Int,
    firstInningsWickets: Int,
    target: Int,
    onStartSecondInnings: () -> Unit,
    onViewScorecard: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Innings Break",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$battingFirstTeam: $firstInningsRuns/$firstInningsWickets",
                style = MaterialTheme.typography.bodyLarge
            )
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "Target: $target",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStartSecondInnings) {
                    Text("Start 2nd Innings")
                }
                OutlinedButton(onClick = onViewScorecard) {
                    Text("Scorecard")
                }
            }
        }
    }
}

// =============================================================================
// Select bowler bottom sheet (dismissible — non-blocking after over end)
// =============================================================================

/**
 * A dismissible [ModalBottomSheet] shown when an over ends and a new bowler must be selected.
 *
 * Unlike the blocking [SelectPlayerDialog] previously used for this flow, this sheet can be
 * swiped down or dismissed by tapping outside.  When dismissed:
 * - The [PendingAction.SelectBowler] state is preserved in the ViewModel (scoring stays gated).
 * - An inline "Next bowler required" banner is shown on the Score tab so the scorer always
 *   knows why the run buttons are disabled.
 *
 * Once a bowler is selected here the pending action clears and scoring resumes normally.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectBowlerBottomSheet(
    availablePlayers: List<Player>,
    savedPlayers: List<PlayerProfile>,
    excludedProfileIds: Set<String> = emptySet(),
    excludedNames: Set<String> = emptySet(),
    onPlayerSelected: (Player) -> Unit,
    onPickFromSaved: (profile: PlayerProfile, isNew: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var showSavedPicker by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Text(
                text = "Select Bowler",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "New over — select the bowler to continue scoring.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (availablePlayers.isEmpty()) {
                Text(
                    text = "No available players to select",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            availablePlayers.forEach { player ->
                TextButton(
                    onClick = { onPlayerSelected(player) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = player.name,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            OutlinedButton(
                onClick = { showSavedPicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Text("Add Player")
            }
        }
    }

    if (showSavedPicker) {
        PlayerPickerDialog(
            savedPlayers = savedPlayers,
            excludedProfileIds = excludedProfileIds,
            excludedNames = excludedNames,
            onDismiss = { showSavedPicker = false },
            onSelect = { profile ->
                showSavedPicker = false
                onPickFromSaved(profile, false)
            },
            onCreateAndSelect = { profile ->
                showSavedPicker = false
                onPickFromSaved(profile, true)
            }
        )
    }
}

// =============================================================================
// Manual result selection dialog
// =============================================================================

/**
 * Modal dialog shown when the scorer taps "End Match" before the match has been
 * naturally completed (win / all-out / overs-limit).
 *
 * Presents four result options and forwards the chosen label to [onResultSelected].
 * The dialog can be cancelled via the Cancel button or back-press, in which case
 * [onDismiss] is called and the match is not ended.
 *
 * @param teamAName  Name of the first team (as configured for the match).
 * @param teamBName  Name of the second team (as configured for the match).
 * @param onResultSelected Called with the human-readable result label chosen by the scorer.
 * @param onDismiss Called when the scorer cancels the dialog without choosing.
 */
@Composable
private fun ManualResultSelectionDialog(
    teamAName: String,
    teamBName: String,
    onResultSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Match Result") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Match not fully completed. Select result:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                listOf(
                    "$teamAName won (manual)",
                    "$teamBName won (manual)",
                    "Match Abandoned",
                    "Match Drawn"
                ).forEach { option ->
                    TextButton(
                        onClick = { onResultSelected(option) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = option,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
