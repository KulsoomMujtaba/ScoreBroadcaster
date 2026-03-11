package com.example.scorebroadcaster.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
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
import com.example.scorebroadcaster.data.InningsPhase
import com.example.scorebroadcaster.data.MatchState
import com.example.scorebroadcaster.data.PendingAction
import com.example.scorebroadcaster.data.ScoreEvent
import com.example.scorebroadcaster.data.ScoringConsoleState
import com.example.scorebroadcaster.data.entity.BattingEntry
import com.example.scorebroadcaster.data.entity.BowlingEntry
import com.example.scorebroadcaster.data.entity.DismissalDetail
import com.example.scorebroadcaster.data.entity.DismissalType
import com.example.scorebroadcaster.data.entity.ExtrasBreakdown
import com.example.scorebroadcaster.data.entity.Match
import com.example.scorebroadcaster.data.entity.Player
import com.example.scorebroadcaster.data.entity.PlayerProfile
import com.example.scorebroadcaster.data.entity.Team
import com.example.scorebroadcaster.data.entity.toMatchPlayer
import com.example.scorebroadcaster.domain.BallEvent
import com.example.scorebroadcaster.viewmodel.MatchViewModel
import com.example.scorebroadcaster.viewmodel.MatchSessionViewModel
import com.example.scorebroadcaster.ui.theme.BoundaryFourContainer
import com.example.scorebroadcaster.ui.theme.OnBoundaryFourContainer
import com.example.scorebroadcaster.ui.theme.BoundarySixContainer
import com.example.scorebroadcaster.ui.theme.OnBoundarySixContainer

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
    modifier: Modifier = Modifier
) {
    val state by matchViewModel.state.collectAsState()
    val console by matchViewModel.consoleState.collectAsState()
    val match by matchViewModel.activeMatch.collectAsState()
    // Capture a non-nullable snapshot so inner lambdas and blocks can smart-cast.
    val activeMatch: Match? = match

    // Show openers-setup dialog when setup is genuinely required.
    // Setup is required when:
    //  - The innings phase is SETUP (fresh innings, never started), OR
    //  - The innings is active (FIRST_INNINGS / SECOND_INNINGS) but the current striker,
    //    non-striker, or bowler is missing — e.g. after an app restart where the live
    //    player references cannot be reconstructed from the persisted event log alone.
    //
    // IMPORTANT: exclude the transient null produced by a wicket falling.  After a wicket
    // updateConsoleAfterEvent sets striker (or nonStriker) to null and simultaneously sets
    // pendingAction = SelectNextBatter.  That null is intentional — the next batter has not
    // yet been chosen — and must NOT be treated as "innings not initialised".  If we did not
    // check for this, the LaunchedEffect below would fire and re-open the innings-setup sheet
    // every time the scorer records a wicket.
    val needsInningsSetup = console.phase == InningsPhase.SETUP ||
            ((console.phase == InningsPhase.FIRST_INNINGS ||
                    console.phase == InningsPhase.SECOND_INNINGS) &&
                    console.pendingAction !is PendingAction.SelectNextBatter &&
                    (console.striker == null ||
                            console.nonStriker == null ||
                            console.currentBowler == null))

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

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // --- Compact match header (always visible above tabs) ---
            CompactMatchHeader(
                match = activeMatch,
                state = state,
                consoleState = console
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

            // --- Last 6 balls ---
            LastBallsRow(lastBalls = state.lastBalls)
            Spacer(modifier = Modifier.height(12.dp))

            // --- Current players card ---
            if (console.phase == InningsPhase.FIRST_INNINGS ||
                console.phase == InningsPhase.SECOND_INNINGS
            ) {
                PlayersSection(console = console)
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

            // --- Scoring buttons ---
            // Scoring is disabled when setup is still pending (players not identified) to
            // prevent silent no-ops in addBallEvent when striker is null.
            val scoringEnabled = (console.phase == InningsPhase.FIRST_INNINGS ||
                    console.phase == InningsPhase.SECOND_INNINGS) &&
                    console.pendingAction == null &&
                    console.striker != null
            // Wicket details dialog state — shown before dispatching the Wicket event
            var showWicketDialog by remember { mutableStateOf(false) }
            // Extras entry dialog state
            var extrasDialogType by remember { mutableStateOf<ExtraType?>(null) }
            ScoringButtonsSection(
                onEvent = { matchViewModel.addEvent(it) },
                onUndo = { matchViewModel.undo() },
                onWicket = { showWicketDialog = true },
                onExtras = { type -> extrasDialogType = type },
                enabled = scoringEnabled
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
                    onConfirm = { dismissal ->
                        showWicketDialog = false
                        matchViewModel.addEvent(ScoreEvent.Wicket(dismissal))
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
                if (currentExtrasType == ExtraType.WIDE || currentExtrasType == ExtraType.NO_BALL) {
                    WideNoBallEntryDialog(
                        initialType = currentExtrasType,
                        striker = console.striker,
                        nonStriker = console.nonStriker,
                        bowlingTeamPlayers = bowlingTeamPlayers,
                        onConfirm = { ballEvent ->
                            extrasDialogType = null
                            matchViewModel.addBallEvent(ballEvent)
                        },
                        onDismiss = { extrasDialogType = null }
                    )
                } else {
                    ExtrasEntryDialog(
                        initialType = currentExtrasType,
                        striker = console.striker,
                        nonStriker = console.nonStriker,
                        bowlingTeamPlayers = bowlingTeamPlayers,
                        onConfirm = { ballEvent ->
                            extrasDialogType = null
                            matchViewModel.addBallEvent(ballEvent)
                        },
                        onDismiss = { extrasDialogType = null }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // --- Innings / match control ---
            if (activeMatch != null) {
                InningsControlSection(
                    console = console,
                    onEndFirstInnings = { matchViewModel.endFirstInnings() },
                    onEndMatch = { matchViewModel.endMatch() }
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
                    AddPlayerToMatchDialog(
                        battingTeamName = console.battingTeamName,
                        bowlingTeamName = console.bowlingTeamName,
                        savedPlayers = savedPlayers,
                        onDismiss = { showAddPlayerDialog = false },
                        onConfirm = { name, toBatting ->
                            val profile = PlayerProfile(displayName = name)
                            onSavePrivatePlayer(profile)
                            matchViewModel.addPlayerToTeam(profile.toMatchPlayer(), toBatting)
                            showAddPlayerDialog = false
                        },
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
                SelectPlayerDialog(
                    title = title,
                    players = action.teamPlayers,
                    teamSectionLabel = "Select from team",
                    emptyTeamMessage = "No unused players left in the batting team",
                    savedPlayers = savedPlayers,
                    onPickFromSaved = { profile, isNew ->
                        if (isNew) onSavePrivatePlayer(profile)
                        val player = profile.toMatchPlayer()
                        matchViewModel.addPlayerToTeam(player, addToBattingTeam = true)
                        matchViewModel.selectNextBatter(player)
                    },
                    onPlayerSelected = { matchViewModel.selectNextBatter(it) },
                    onAddNewPlayer = { name ->
                        val profile = PlayerProfile(displayName = name)
                        onSavePrivatePlayer(profile)
                        val player = profile.toMatchPlayer()
                        matchViewModel.addPlayerToTeam(player, addToBattingTeam = true)
                        matchViewModel.selectNextBatter(player)
                    },
                    onAllOut = { matchViewModel.endInningsAsAllOut() }
                )
            }
            is PendingAction.SelectBowler -> SelectPlayerDialog(
                title = "Select Bowler",
                players = action.availablePlayers,
                savedPlayers = savedPlayers,
                onPickFromSaved = { profile, isNew ->
                    if (isNew) onSavePrivatePlayer(profile)
                    val player = profile.toMatchPlayer()
                    matchViewModel.addPlayerToTeam(player, addToBattingTeam = false)
                    matchViewModel.changeBowler(player)
                },
                onPlayerSelected = { matchViewModel.changeBowler(it) },
                onAddNewPlayer = { name ->
                    val profile = PlayerProfile(displayName = name)
                    onSavePrivatePlayer(profile)
                    val player = profile.toMatchPlayer()
                    matchViewModel.addPlayerToTeam(player, addToBattingTeam = false)
                    matchViewModel.changeBowler(player)
                }
            )
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
    }
}

// =============================================================================
// Compact match header (shown above tabs)
// =============================================================================

@Composable
private fun CompactMatchHeader(
    match: Match?,
    state: MatchState,
    consoleState: ScoringConsoleState
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
        color = MaterialTheme.colorScheme.tertiaryContainer,
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
            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
        )
    }
}

// =============================================================================
// Last balls row
// =============================================================================

@Composable
private fun LastBallsRow(lastBalls: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        lastBalls.forEach { ball ->
            val bgColor = when {
                ball == "W" -> MaterialTheme.colorScheme.error
                ball.startsWith("Wd") || ball.startsWith("NB") -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.primary
            }
            Surface(color = bgColor, shape = MaterialTheme.shapes.small) {
                Text(
                    text = ball,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

// =============================================================================
// Current players card
// =============================================================================

@Composable
private fun PlayersSection(console: ScoringConsoleState) {
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "At the Crease",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )

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
                    Text("⚾ ${it.name}", style = MaterialTheme.typography.bodySmall)
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
            text = "⚾ ${entry.player.name}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            // Format: overs.balls - maidens - runs - wickets  (e.g. 3.0-1-12-2)
            text = "${entry.overs}.${entry.balls}-${entry.maidens}-${entry.runs}-${entry.wickets}w",
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
    colors: androidx.compose.material3.ButtonColors = ButtonDefaults.buttonColors()
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        modifier = modifier.defaultMinSize(minHeight = 52.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
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
 * Boundary buttons (4 and 6) use theme-aware container colours to stand out.
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
        // Row 1: 0, 1, 2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(0, 1, 2).forEach { run ->
                ScoringActionButton(
                    text = "$run",
                    onClick = { onEvent(ScoreEvent.Run(run)) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        // Row 2: 3, 4, 6 — boundary buttons subtly highlighted
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ScoringActionButton(
                text = "3",
                onClick = { onEvent(ScoreEvent.Run(3)) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            ScoringActionButton(
                text = "4",
                onClick = { onEvent(ScoreEvent.Run(4)) },
                enabled = enabled,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BoundaryFourContainer,
                    contentColor = OnBoundaryFourContainer
                )
            )
            ScoringActionButton(
                text = "6",
                onClick = { onEvent(ScoreEvent.Run(6)) },
                enabled = enabled,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BoundarySixContainer,
                    contentColor = OnBoundarySixContainer
                )
            )
        }
    }
}

/**
 * 2 × 2 grid of extras buttons (Wide, No Ball, Bye, Leg Bye).
 * Styled as OutlinedButtons to appear secondary to run buttons.
 */
@Composable
private fun ExtrasButtonsGrid(
    onExtras: (ExtraType) -> Unit,
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
    }
}

/**
 * Side-by-side Wicket and Undo buttons.
 * Wicket uses errorContainer for a destructive appearance.
 * Undo uses secondaryContainer and is always clickable (matches original behaviour).
 */
@Composable
private fun ActionButtonsRow(
    onWicket: () -> Unit,
    onUndo: () -> Unit,
    wicketEnabled: Boolean
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
        OutlinedButton(
            onClick = onUndo,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 52.dp)
        ) {
            Text("Undo", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Full scoring control pad split into three labelled sections:
 * Runs → Extras → Actions (Wicket / Undo).
 */
@Composable
private fun ScoringButtonsSection(
    onEvent: (ScoreEvent) -> Unit,
    onUndo: () -> Unit,
    onWicket: () -> Unit,
    onExtras: (ExtraType) -> Unit,
    enabled: Boolean
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
            ExtrasButtonsGrid(onExtras = onExtras, enabled = enabled)
        }
        ScoringControlsSection(label = "Actions") {
            ActionButtonsRow(onWicket = onWicket, onUndo = onUndo, wicketEnabled = enabled)
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
                val result = when {
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
 * @param savedPlayers When non-empty and [onPickFromSaved] is non-null, a "Pick from saved
 *   players" button is shown so the scorer can select or create a player from the saved list.
 * @param onPickFromSaved Optional callback triggered when a player is chosen (or created) via
 *   [PlayerPickerDialog]. Receives the [PlayerProfile] and an [isNew] flag so the caller can
 *   persist the profile if it was freshly created.
 * @param onAddNewPlayer Optional callback: when non-null, an "Add new player" inline field
 *   is shown so the scorer can create a player on the fly without closing this dialog.
 *   The callback receives the trimmed player name.
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
    onPickFromSaved: ((profile: PlayerProfile, isNew: Boolean) -> Unit)? = null,
    onAddNewPlayer: ((String) -> Unit)? = null,
    onAllOut: (() -> Unit)? = null
) {
    var newPlayerName by remember { mutableStateOf("") }
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
                    } else if (onAddNewPlayer == null) {
                        Text("No players available.")
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
                // --- Pick from saved players (secondary path) ---
                if (onPickFromSaved != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "Saved players",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    OutlinedButton(
                        onClick = { showSavedPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Pick from saved players")
                    }
                }
                // --- Add new player (tertiary path) ---
                if (onAddNewPlayer != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "Add new player",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newPlayerName,
                            onValueChange = { newPlayerName = it },
                            label = { Text("Player name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val name = newPlayerName.trim()
                                if (name.isNotEmpty()) {
                                    onAddNewPlayer(name)
                                    newPlayerName = ""
                                }
                            },
                            enabled = newPlayerName.isNotBlank()
                        ) { Text("Add") }
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

    // PlayerPickerDialog shown when "Pick from saved players" is tapped
    if (showSavedPicker && onPickFromSaved != null) {
        PlayerPickerDialog(
            savedPlayers = savedPlayers,
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
    onConfirm: (DismissalDetail) -> Unit,
    onDismiss: () -> Unit
) {
    // Default to striker out (the most common case)
    var batterOut by remember { mutableStateOf(striker ?: nonStriker) }
    var selectedType by remember { mutableStateOf(DismissalType.BOWLED) }
    var selectedFielder by remember { mutableStateOf<Player?>(null) }
    var selectedFielder2 by remember { mutableStateOf<Player?>(null) }
    var showSecondFielder by remember { mutableStateOf(false) }

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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        types.take(3).forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = {
                                    selectedType = type
                                    selectedFielder = null
                                    selectedFielder2 = null
                                    showSecondFielder = false
                                },
                                label = { Text(type.label) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        types.drop(3).forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = {
                                    selectedType = type
                                    selectedFielder = null
                                    selectedFielder2 = null
                                    showSecondFielder = false
                                },
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val out = batterOut ?: return@Button
                    val fieldersList = when {
                        !requiresFielder -> emptyList()
                        selectedType == DismissalType.RUN_OUT ->
                            listOfNotNull(selectedFielder, selectedFielder2)
                        else -> listOfNotNull(selectedFielder)
                    }
                    onConfirm(
                        DismissalDetail(
                            batter = out,
                            dismissalType = selectedType,
                            fielders = fieldersList,
                            bowler = currentBowler
                        )
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

    // Reset wicket state whenever the extra type changes
    LaunchedEffect(extraType) {
        hasWicket = false
        batterOut = striker ?: nonStriker
        selectedFielder = null
    }

    val totalRuns = if (useCustomRuns) customRunsText.toIntOrNull()?.coerceAtLeast(1) ?: selectedRuns else selectedRuns

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
                    val ballEvent = buildExtrasEvent(extraType, totalRuns, hasWicket, batterOut, selectedFielder)
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
 * - **Bye / Leg Bye**: all runs go directly to byes / leg-byes.
 *
 * A wicket on an extras delivery is always a **Run Out** and does NOT credit the bowler.
 */
private fun buildExtrasEvent(
    type: ExtraType,
    runs: Int,
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
            extras = ExtrasBreakdown(byes = runs),
            wicket = hasWicket,
            dismissalDetail = dismissal,
            countsAsBall = true
        )
        ExtraType.LEG_BYE -> BallEvent(
            extras = ExtrasBreakdown(legByes = runs),
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
 * Dedicated dialog for Wide and No Ball deliveries.
 *
 * The dialog makes the automatic +1 extra run explicit:
 * - The scorer enters only the **additional** runs taken by running (0 by default).
 * - The final extras value is always `1 + additionalRuns`.
 *
 * Wide:  total wides = 1 + additionalRuns; countsAsBall = false.
 * No Ball: noBalls = 1; runsOffBat = additionalRuns; countsAsBall = false.
 */
@Composable
internal fun WideNoBallEntryDialog(
    initialType: ExtraType,
    striker: Player?,
    nonStriker: Player?,
    bowlingTeamPlayers: List<Player>,
    onConfirm: (BallEvent) -> Unit,
    onDismiss: () -> Unit
) {
    var extraType by remember { mutableStateOf(initialType) }
    var selectedAdditionalRuns by remember { mutableStateOf(0) }
    var customRunsText by remember { mutableStateOf("") }
    var useCustomRuns by remember { mutableStateOf(false) }
    var hasWicket by remember { mutableStateOf(false) }
    var batterOut by remember { mutableStateOf(striker ?: nonStriker) }
    var selectedFielder by remember { mutableStateOf<Player?>(null) }

    // Reset wicket state and runs whenever the extra type changes
    LaunchedEffect(extraType) {
        hasWicket = false
        batterOut = striker ?: nonStriker
        selectedFielder = null
        selectedAdditionalRuns = 0
        customRunsText = ""
        useCustomRuns = false
    }

    val additionalRuns = if (useCustomRuns) customRunsText.toIntOrNull()?.coerceAtLeast(0) ?: selectedAdditionalRuns else selectedAdditionalRuns
    val totalRuns = 1 + additionalRuns

    val isValid = !hasWicket || batterOut != null

    val runsWord = if (additionalRuns == 1) "run" else "runs"
    val summaryText = if (extraType == ExtraType.WIDE) {
        "Total extras on this ball: $totalRuns (1 wide + $additionalRuns $runsWord)"
    } else {
        "Total on this ball: $totalRuns (1 no-ball + $additionalRuns additional $runsWord)"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(extraType.label) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // --- Automatic extra explanation ---
                Text(
                    "Includes 1 automatic extra run",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Add any additional runs taken by running",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                HorizontalDivider()

                // --- Extra type selector (Wide / No Ball only) ---
                Text("Extra type", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(ExtraType.WIDE, ExtraType.NO_BALL).forEach { type ->
                        FilterChip(
                            selected = extraType == type,
                            onClick = { extraType = type },
                            label = { Text(type.label) }
                        )
                    }
                }

                HorizontalDivider()

                // --- Additional runs selector ---
                val runsLabel = if (extraType == ExtraType.WIDE) "Additional runs taken" else "Additional runs after no-ball"
                val supportingLabel = if (extraType == ExtraType.WIDE) "1 wide is added automatically" else "1 no-ball is added automatically"
                Text(runsLabel, style = MaterialTheme.typography.labelMedium)
                Text(
                    supportingLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 1, 2, 3, 4).forEach { runs ->
                        FilterChip(
                            selected = !useCustomRuns && selectedAdditionalRuns == runs,
                            onClick = { selectedAdditionalRuns = runs; useCustomRuns = false },
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
                        label = { Text("Additional runs") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider()

                // --- Summary ---
                Text(
                    summaryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                HorizontalDivider()

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
                    val ballEvent = buildWideNoBallEvent(extraType, additionalRuns, hasWicket, batterOut, selectedFielder)
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
 * Constructs a [BallEvent] for a Wide or No Ball delivery.
 *
 * - **Wide**: wides = 1 + additionalRuns; runsOffBat = 0; countsAsBall = false.
 * - **No Ball**: noBalls = 1; runsOffBat = additionalRuns; countsAsBall = false.
 *
 * [additionalRuns] is the runs taken by running AFTER the automatic +1 extra.
 */
private fun buildWideNoBallEvent(
    type: ExtraType,
    additionalRuns: Int,
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
            extras = ExtrasBreakdown(wides = 1 + additionalRuns),
            wicket = hasWicket,
            dismissalDetail = dismissal,
            countsAsBall = false
        )
        ExtraType.NO_BALL -> BallEvent(
            runsOffBat = additionalRuns,
            extras = ExtrasBreakdown(noBalls = 1),
            wicket = hasWicket,
            dismissalDetail = dismissal,
            countsAsBall = false
        )
        else -> error("buildWideNoBallEvent called with non-wide/no-ball type: $type")
    }
}

// =============================================================================
// Add player during an active match
// =============================================================================

@Composable
private fun AddPlayerToMatchDialog(
    battingTeamName: String,
    bowlingTeamName: String,
    savedPlayers: List<PlayerProfile> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (name: String, addToBattingTeam: Boolean) -> Unit,
    /**
     * Called when a player is chosen or created via [PlayerPickerDialog].
     * [isNew] is true when the player was created inline (caller should persist the profile).
     */
    onPickFromSaved: ((profile: PlayerProfile, isNew: Boolean, addToBattingTeam: Boolean) -> Unit)? = null
) {
    var playerName by remember { mutableStateOf("") }
    var addToBatting by remember { mutableStateOf(true) }
    var showSavedPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Player") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = playerName,
                    onValueChange = { playerName = it },
                    label = { Text("Player name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Add to", style = MaterialTheme.typography.labelMedium)
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
                if (onPickFromSaved != null) {
                    OutlinedButton(
                        onClick = { showSavedPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Pick from saved players")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(playerName.trim(), addToBatting) },
                enabled = playerName.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showSavedPicker && onPickFromSaved != null) {
        PlayerPickerDialog(
            savedPlayers = savedPlayers,
            onDismiss = { showSavedPicker = false },
            onSelect = { profile ->
                showSavedPicker = false
                onPickFromSaved(profile, false, addToBatting)
            },
            onCreateAndSelect = { profile ->
                showSavedPicker = false
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
    // Auto-select defaults on first composition; when the roster changes later,
    // keep the current selection if it is still valid rather than resetting.
    var striker by remember { mutableStateOf(battingTeam.players.getOrNull(0)) }
    var nonStriker by remember { mutableStateOf(battingTeam.players.getOrNull(1)) }
    var bowler by remember { mutableStateOf(bowlingTeam.players.getOrNull(0)) }

    // When batting team roster changes (e.g. a new player was added mid-setup),
    // refresh the selection only if the previously chosen player is no longer present.
    LaunchedEffect(battingTeam.players) {
        if (striker == null || battingTeam.players.none { it.id == striker?.id }) {
            striker = battingTeam.players.firstOrNull { it.id != nonStriker?.id }
        }
        if (nonStriker == null || battingTeam.players.none { it.id == nonStriker?.id }) {
            nonStriker = battingTeam.players.firstOrNull { it.id != striker?.id }
        }
    }
    LaunchedEffect(bowlingTeam.players) {
        if (bowler == null || bowlingTeam.players.none { it.id == bowler?.id }) {
            bowler = bowlingTeam.players.firstOrNull()
        }
    }
    // Dialog-chain state:
    // addPlayerChoiceFor: true = show choice dialog for batting team, false = bowling team
    // addPlayerNewFor:    true = show "add new player" dialog for batting, false = bowling
    // pickerForBatting:   true = show saved-player picker for batting, false = bowling
    var addPlayerChoiceFor by remember { mutableStateOf<Boolean?>(null) }
    var addPlayerNewFor by remember { mutableStateOf<Boolean?>(null) }
    var pickerForBatting by remember { mutableStateOf<Boolean?>(null) }

    val inningsLabel = when (inningsNumber) {
        1 -> "Start 1st Innings"
        2 -> "Start 2nd Innings"
        else -> "Start Innings"
    }
    val needsMoreBatters = battingTeam.players.size < 2
    val needsMoreBowlers = bowlingTeam.players.isEmpty()
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
                players = battingTeam.players,
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
                players = battingTeam.players,
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
                onClick = { addPlayerChoiceFor = true },
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
                players = bowlingTeam.players,
                selected = bowler,
                onSelected = { bowler = it }
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Add Bowler button
            OutlinedButton(
                onClick = { addPlayerChoiceFor = false },
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

    // ── Add-player dialog chain ───────────────────────────────────────────────
    val choiceTarget = addPlayerChoiceFor
    if (choiceTarget != null) {
        val teamName = if (choiceTarget) battingTeam.name else bowlingTeam.name
        AddPlayerChoiceDialog(
            teamName = teamName,
            showSavedOption = onPickFromSaved != null,
            onPickFromSaved = {
                addPlayerChoiceFor = null
                pickerForBatting = choiceTarget
            },
            onAddNew = {
                addPlayerChoiceFor = null
                addPlayerNewFor = choiceTarget
            },
            onDismiss = { addPlayerChoiceFor = null }
        )
    }

    val newTarget = addPlayerNewFor
    if (newTarget != null) {
        val teamName = if (newTarget) battingTeam.name else bowlingTeam.name
        AddNewPlayerDialog(
            teamName = teamName,
            onAdd = { name ->
                if (newTarget) onAddPlayerToBattingTeam(name)
                else onAddPlayerToBowlingTeam(name)
                addPlayerNewFor = null
            },
            onDismiss = { addPlayerNewFor = null }
        )
    }

    // Saved-player picker for batting or bowling team
    val pickerTarget = pickerForBatting
    if (pickerTarget != null && onPickFromSaved != null) {
        PlayerPickerDialog(
            savedPlayers = savedPlayers,
            onDismiss = { pickerForBatting = null },
            onSelect = { profile ->
                pickerForBatting = null
                onPickFromSaved(profile, false, pickerTarget)
            },
            onCreateAndSelect = { profile ->
                pickerForBatting = null
                onPickFromSaved(profile, true, pickerTarget)
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
// Add-player dialogs (used in SetupOpenersBottomSheet)
// =============================================================================

/**
 * Choice dialog shown when the scorer taps "+ Add Batter" or "+ Add Bowler".
 * Offers two paths: pick from saved players or create a new one inline.
 */
@Composable
private fun AddPlayerChoiceDialog(
    teamName: String,
    showSavedOption: Boolean,
    onPickFromSaved: () -> Unit,
    onAddNew: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Player to $teamName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (showSavedOption) {
                    TextButton(
                        onClick = onPickFromSaved,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Pick from saved players") }
                }
                TextButton(
                    onClick = onAddNew,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add new player") }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Small dialog that lets the scorer type a name and add a brand-new player
 * to a specific team during innings setup.
 */
@Composable
private fun AddNewPlayerDialog(
    teamName: String,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Player – $teamName") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Player name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onAdd(name.trim()) },
                enabled = name.trim().isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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
