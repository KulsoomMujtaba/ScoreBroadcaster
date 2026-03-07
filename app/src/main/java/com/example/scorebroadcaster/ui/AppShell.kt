package com.example.scorebroadcaster.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Bottom navigation tabs
// ---------------------------------------------------------------------------

enum class BottomNavTab(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    HOME("home", "Home", Icons.Default.Home),
    MATCHES("my_matches", "Matches", Icons.Default.List),
    SCORE("score_tab", "Score", Icons.Default.Star),
    LIVE("live_hub", "Live", Icons.Default.PlayArrow)
}

private fun selectedTab(route: String?): BottomNavTab? = when (route) {
    "home" -> BottomNavTab.HOME
    "my_matches", "create_match", "player_setup", "match_summary", "match_details"
        -> BottomNavTab.MATCHES
    "score_tab", "scoring_only", "scorecard", "ball_timeline" -> BottomNavTab.SCORE
    "live_hub", "live_preview", "stream_setup", "stream_preview" -> BottomNavTab.LIVE
    else -> null
}

private fun topBarTitle(route: String?): String = when (route) {
    "home" -> "Scored"
    "my_matches" -> "My Matches"
    "score_tab", "scoring_only" -> "Score"
    "live_hub" -> "Live"
    "create_match" -> "Create Match"
    "player_setup" -> "Player Setup"
    "match_summary" -> "Match Summary"
    "match_details" -> "Match Details"
    "scorecard" -> "Scorecard"
    "ball_timeline" -> "Over History"
    "live_preview" -> "Camera Preview"
    "stream_setup" -> "Stream Setup"
    "stream_preview" -> "Go Live"
    "saved_teams" -> "Saved Teams"
    "saved_players" -> "Saved Players"
    else -> "Scored"
}

/** Routes that are primary tab destinations — show the hamburger menu icon. */
private val primaryRoutes = setOf("home", "my_matches", "score_tab", "live_hub")

// ---------------------------------------------------------------------------
// AppShell
// ---------------------------------------------------------------------------

/**
 * Root scaffold for Scored.
 *
 * Wraps the app content in:
 * - a [ModalNavigationDrawer] for secondary destinations
 * - a [TopAppBar] with a drawer toggle (primary routes) or back arrow (secondary routes)
 * - a [NavigationBar] with the four primary bottom-nav tabs
 *
 * Navigation logic is left to the caller via [navController].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    navController: NavController,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isPrimary = currentRoute in primaryRoutes

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    scope.launch { drawerState.close() }
                    navController.navigate(route) {
                        launchSingleTop = true
                    }
                }
            )
        }
    ) {
        Scaffold(
            modifier = modifier,
            topBar = {
                TopAppBar(
                    title = { Text(topBarTitle(currentRoute)) },
                    navigationIcon = {
                        if (isPrimary) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Open navigation menu")
                            }
                        } else {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                NavigationBar {
                    BottomNavTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab(currentRoute) == tab,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        ) { paddingValues ->
            content(paddingValues)
        }
    }
}

// ---------------------------------------------------------------------------
// Navigation drawer
// ---------------------------------------------------------------------------

/**
 * Side-drawer with primary, secondary, and utility navigation destinations.
 */
@Composable
fun AppDrawer(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(modifier = modifier) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Scored",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Primary destinations
        DrawerNavItem(
            icon = Icons.Default.Home,
            label = "Home",
            selected = currentRoute == "home",
            onClick = { onNavigate("home") }
        )
        DrawerNavItem(
            icon = Icons.Default.List,
            label = "My Matches",
            selected = currentRoute == "my_matches",
            onClick = { onNavigate("my_matches") }
        )
        DrawerNavItem(
            icon = Icons.Default.Add,
            label = "Create Match",
            selected = currentRoute == "create_match",
            onClick = { onNavigate("create_match") }
        )
        DrawerNavItem(
            icon = Icons.Default.Star,
            label = "Saved Teams",
            selected = currentRoute == "saved_teams",
            onClick = { onNavigate("saved_teams") }
        )
        DrawerNavItem(
            icon = Icons.Default.Person,
            label = "Saved Players",
            selected = currentRoute == "saved_players",
            onClick = { onNavigate("saved_players") }
        )
        DrawerNavItem(
            icon = Icons.Default.List,
            label = "Scorecard",
            selected = currentRoute == "scorecard",
            onClick = { onNavigate("scorecard") }
        )
        DrawerNavItem(
            icon = Icons.Default.Info,
            label = "Ball Timeline",
            selected = currentRoute == "ball_timeline",
            onClick = { onNavigate("ball_timeline") }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Broadcast destinations
        DrawerNavItem(
            icon = Icons.Default.PlayArrow,
            label = "Camera Preview",
            selected = currentRoute == "live_preview",
            onClick = { onNavigate("live_preview") }
        )
        DrawerNavItem(
            icon = Icons.Default.Share,
            label = "Stream Setup",
            selected = currentRoute == "stream_setup",
            onClick = { onNavigate("stream_setup") }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Utility (placeholder)
        DrawerNavItem(
            icon = Icons.Default.Settings,
            label = "Settings",
            selected = false,
            onClick = { /* placeholder – no-op until settings screen is added */ }
        )
        DrawerNavItem(
            icon = Icons.Default.Info,
            label = "About",
            selected = false,
            onClick = { /* placeholder – no-op until about screen is added */ }
        )
    }
}

@Composable
private fun DrawerNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}

// ---------------------------------------------------------------------------
// Score tab empty state
// ---------------------------------------------------------------------------

/**
 * Shown in the Score bottom-nav tab when there is no active match.
 * Guides the user to create or select a match before scoring can begin.
 */
@Composable
fun ScoreEmptyState(
    onCreateMatchClick: () -> Unit,
    onMyMatchesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No active match",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Create a match or select an existing one to start scoring ball by ball.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onMyMatchesClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "My Matches")
                    }
                    Button(
                        onClick = onCreateMatchClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "Create Match")
                    }
                }
            }
        }
    }
}
