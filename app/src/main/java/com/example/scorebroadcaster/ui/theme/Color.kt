package com.example.scorebroadcaster.ui.theme

import androidx.compose.ui.graphics.Color

// =============================================================================
// Cricket-inspired brand palette — Cricinfo-style visual identity
// Primary brand: cricket green | Surfaces: clean white | Text: dark readable
// =============================================================================

// Primary brand greens
val CricketGreen = Color(0xFF008F5A)           // Primary brand green
val CricketDarkGreen = Color(0xFF006C44)        // Primary dark green
val CricketLightGreen = Color(0xFFDDF4EA)       // Primary light green / container

// Backgrounds and surfaces
val CricketBackground = Color(0xFFF7F9F8)       // App background — very light neutral
val CricketSurfaceVariant = Color(0xFFEEF3F0)   // Surface variant / subtle card fill
val CricketOutline = Color(0xFFD9E2DD)          // Dividers and borders

// Text hierarchy
val CricketTextPrimary = Color(0xFF0F1720)      // On-surface / primary dark text
val CricketTextSecondary = Color(0xFF5B6871)    // On-surface-variant / secondary text

// Error / Wicket state
val CricketError = Color(0xFFC83A3A)            // Wicket / destructive action
val CricketErrorContainer = Color(0xFFFFDAD6)   // Light error container
val OnCricketError = Color(0xFFFFFFFF)
val OnCricketErrorContainer = Color(0xFF410002)

// Cricket semantic accents — boundary buttons and chips
// Four (4): subtle light-green emphasis
val BoundaryFourContainer = Color(0xFFCEF0DF)   // Light green container for 4 buttons/chips
val OnBoundaryFourContainer = Color(0xFF004D2E) // Dark green text on four container

// Six (6): stronger, darker green emphasis
val BoundarySixContainer = Color(0xFF0E6B43)    // Dark green container for 6 buttons/chips
val OnBoundarySixContainer = Color(0xFFFFFFFF)  // White text on six container

// Extras: warm amber accent
val ExtrasAccentContainer = Color(0xFFFFE8B2)   // Light amber for extras chips
val OnExtrasAccentContainer = Color(0xFF3B2500) // Dark text on extras

// Secondary brand colour (four accent mid-tone — M3 secondary role)
val FourAccentMid = Color(0xFF5FAF84)           // Four secondary mid-tone

// Extras amber (M3 tertiary role)
val ExtrasAmber = Color(0xFFA66A00)             // Warm amber for extras