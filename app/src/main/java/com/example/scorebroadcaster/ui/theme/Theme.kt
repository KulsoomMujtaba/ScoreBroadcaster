package com.example.scorebroadcaster.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// =============================================================================
// Cricket-green light theme — primary brand identity
// =============================================================================

private val LightColorScheme = lightColorScheme(
    // Primary: cricket brand green
    primary = CricketGreen,
    onPrimary = Color.White,
    primaryContainer = CricketLightGreen,
    onPrimaryContainer = CricketTextPrimary,

    // Secondary: four-accent mid-green (boundary highlight secondary role)
    secondary = FourAccentMid,
    onSecondary = Color.White,
    secondaryContainer = BoundaryFourContainer,
    onSecondaryContainer = OnBoundaryFourContainer,

    // Tertiary: warm amber for extras
    tertiary = ExtrasAmber,
    onTertiary = Color.White,
    tertiaryContainer = ExtrasAccentContainer,
    onTertiaryContainer = OnExtrasAccentContainer,

    // Error / Wicket: clear red
    error = CricketError,
    onError = OnCricketError,
    errorContainer = CricketErrorContainer,
    onErrorContainer = OnCricketErrorContainer,

    // Backgrounds and surfaces: clean, minimal
    background = CricketBackground,
    onBackground = CricketTextPrimary,
    surface = Color.White,
    onSurface = CricketTextPrimary,
    surfaceVariant = CricketSurfaceVariant,
    onSurfaceVariant = CricketTextSecondary,
    outline = CricketOutline,
)

// Dark scheme retains the cricket-green identity with inverted contrasts.
private val DarkColorScheme = darkColorScheme(
    primary = CricketLightGreen,
    onPrimary = CricketDarkGreen,
    primaryContainer = CricketDarkGreen,
    onPrimaryContainer = CricketLightGreen,
    secondary = FourAccentMid,
    onSecondary = CricketTextPrimary,
    secondaryContainer = Color(0xFF1E4D36),
    onSecondaryContainer = BoundaryFourContainer,
    tertiary = ExtrasAmber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF3B2500),
    onTertiaryContainer = ExtrasAccentContainer,
    error = CricketError,
    onError = OnCricketError,
    errorContainer = Color(0xFF6B1111),
    onErrorContainer = CricketErrorContainer,
)

@Composable
fun ScoreBroadcasterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic colour is disabled by default to preserve the cricket-green brand identity.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}