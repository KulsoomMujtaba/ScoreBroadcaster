package com.devhub.scored.navigation
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.launch

private const val PRIVACY_POLICY_URL =
    "https://docs.google.com/document/d/1d8c6IOqUwHz7jXLD33xIEWFWMAmEkf8rmmvsK5wi1Hw/edit?pli=1&tab=t.0"

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

private fun selectedTab(route: String?): BottomNavTab? = when {
    route == BottomNavTab.HOME.route -> BottomNavTab.HOME
    route == BottomNavTab.MATCHES.route || route == "create_match" || route == "player_setup" ||
        route == "match_summary" || route == "match_details" -> BottomNavTab.MATCHES
    route == BottomNavTab.SCORE.route || route == "scoring_only" || route == "scorecard" ||
        route == "ball_timeline" || route == "match_preview" -> BottomNavTab.SCORE
    route == BottomNavTab.LIVE.route || route == "live_preview" || route == "stream_setup" ||
        route == "stream_preview" -> BottomNavTab.LIVE
    else -> null
}

private fun topBarTitle(route: String?): String = when {
    route == "home" -> "Scored"
    route == "my_matches" -> "My Matches"
    route == "score_tab" || route == "scoring_only" -> "Score"
    route == "live_hub" -> "Live"
    route == "create_match" -> "Create Match"
    route == "player_setup" -> "Player Setup"
    route == "match_summary" -> "Match Summary"
    route == "match_details" -> "Match Details"
    route == "scorecard" -> "Scorecard"
    route == "ball_timeline" -> "Over History"
    route == "match_preview" -> "Match Preview"
    route == "live_preview" -> "Camera Preview"
    route == "stream_setup" -> "Stream Setup"
    route == "stream_preview" -> "Go Live"
    route == "saved_teams" -> "Saved Teams"
    route == "saved_players" -> "My Players"
    route == "enter_share_code" -> "Watch a Match"
    route?.startsWith("match_viewer") == true -> "Match Viewer"
    else -> "Scored"
}

/** Routes that are primary tab destinations — show the hamburger menu icon. */
private val primaryRoutes = setOf("home", "my_matches", "score_tab", "live_hub")

/** Routes that render as immersive full-screen previews — hide all app chrome. */
private val immersiveRoutes = setOf("live_preview", "stream_preview")

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
 * For immersive routes (`live_preview`, `stream_preview`) all chrome is hidden so the
 * preview occupies the full screen.
 *
 * Navigation logic is left to the caller via [navController].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    navController: NavController,
    onSignOut: () -> Unit = {},
    onDeleteAccount: () -> Unit = {},
    signedInEmail: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isPrimary = currentRoute in primaryRoutes
    val isImmersive = currentRoute in immersiveRoutes

    if (isImmersive) {
        // Full-screen immersive mode: no top bar, bottom bar, or drawer chrome.
        Box(modifier = modifier.fillMaxSize()) {
            content(PaddingValues())
        }
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                currentRoute = currentRoute,
                signedInEmail = signedInEmail,
                onNavigate = { route ->
                    scope.launch { drawerState.close() }
                    navController.navigate(route) {
                        launchSingleTop = true
                    }
                },
                onSignOut = {
                    scope.launch { drawerState.close() }
                    onSignOut()
                },
                onDeleteAccount = {
                    scope.launch { drawerState.close() }
                    onDeleteAccount()
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
                                    // restoreState must be false for the HOME/start-destination tab.
                                    // popUpTo(startDest) { saveState = true } saves state keyed to
                                    // the start destination's ID; navigating to HOME with
                                    // restoreState = true would find that saved state and incorrectly
                                    // re-add the previously-popped tab destinations on top of HOME.
                                    restoreState = tab != BottomNavTab.HOME
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
    onSignOut: () -> Unit = {},
    onDeleteAccount: () -> Unit = {},
    signedInEmail: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Two-stage delete account confirmation state.
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteVerifyDialog by remember { mutableStateOf(false) }
    var deleteConfirmInput by remember { mutableStateOf("") }

    ModalDrawerSheet(modifier = modifier) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Scored",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        if (signedInEmail != null) {
            Text(
                text = signedInEmail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }
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
            label = "My Players",
            selected = currentRoute == "saved_players",
            onClick = { onNavigate("saved_players") }
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

        // Viewer — enter share code to watch a published match
        DrawerNavItem(
            icon = Icons.Default.Info,
            label = "Watch a Match",
            selected = currentRoute == "enter_share_code" || currentRoute == "match_viewer",
            onClick = { onNavigate("enter_share_code") }
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
        DrawerNavItem(
            icon = Icons.Default.Lock,
            label = "Privacy Policy",
            selected = false,
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))
                val resolved = context.packageManager
                    .resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY) != null
                if (resolved) {
                    Log.d("AppDrawer", "Privacy Policy opened")
                    context.startActivity(intent)
                } else {
                    Toast.makeText(context, "Unable to open link", Toast.LENGTH_SHORT).show()
                }
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        DrawerNavItem(
            icon = Icons.Default.ExitToApp,
            label = "Sign Out",
            selected = false,
            onClick = onSignOut
        )

        DrawerNavItem(
            icon = Icons.Default.Delete,
            label = "Delete Account",
            selected = false,
            onClick = { showDeleteConfirmDialog = true }
        )
    }

    // Stage 1 — initial confirmation dialog.
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Account?") },
            text = {
                Text(
                    "This will permanently delete your account and all your data. " +
                        "This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        deleteConfirmInput = ""
                        showDeleteVerifyDialog = true
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Stage 2 — type "DELETE" to confirm.
    if (showDeleteVerifyDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteVerifyDialog = false
                deleteConfirmInput = ""
            },
            title = { Text("Are you absolutely sure?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Type DELETE to confirm permanent account deletion.")
                    OutlinedTextField(
                        value = deleteConfirmInput,
                        onValueChange = { deleteConfirmInput = it },
                        singleLine = true,
                        placeholder = { Text("DELETE") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = deleteConfirmInput == "DELETE",
                    onClick = {
                        showDeleteVerifyDialog = false
                        deleteConfirmInput = ""
                        onDeleteAccount()
                    }
                ) {
                    Text(
                        "Confirm",
                        color = if (deleteConfirmInput == "DELETE")
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteVerifyDialog = false
                        deleteConfirmInput = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
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
