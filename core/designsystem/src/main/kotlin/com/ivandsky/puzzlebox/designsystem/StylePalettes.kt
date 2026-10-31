package com.ivandsky.puzzlebox.designsystem

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Ticket 08 palette prototypes. These ADD around the shipped token set (ColorTokens.kt
// is untouched); both are light looks by design — light surfaces are their identity —
// and every on-color pair holds WCAG AA (>= 4.5:1) under the same guard the control
// schemes are tested with.

// C2 "Clean light" — Quizlet pole: near-white cool surfaces, one vivid cobalt accent,
// quiet slate/teal support roles. Separation comes from small tone steps, never lines.
val CleanLightColorScheme = lightColorScheme(
    primary = Color(0xFF2B5CE7),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE3FF),
    onPrimaryContainer = Color(0xFF0B1C6E),
    secondary = Color(0xFF575D73),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDBE0F5),
    onSecondaryContainer = Color(0xFF141A2E),
    tertiary = Color(0xFF226B61),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCDF0E9),
    onTertiaryContainer = Color(0xFF0E2B27),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surface = Color(0xFFFBFBFD),
    onSurface = Color(0xFF1A1B22),
    surfaceVariant = Color(0xFFE5E7F0),
    onSurfaceVariant = Color(0xFF454A5C),
    outline = Color(0xFF878DA1),
    outlineVariant = Color(0xFFD4D7E3),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2C2E3A),
    inverseOnSurface = Color(0xFFF1F1F9),
    inversePrimary = Color(0xFFB5C2FF),
    surfaceDim = Color(0xFFDBDCE6),
    surfaceBright = Color(0xFFFBFBFD),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F6FA),
    surfaceContainer = Color(0xFFF0F1F7),
    surfaceContainerHigh = Color(0xFFEAEBF3),
    surfaceContainerHighest = Color(0xFFE4E5EE),
)

// C3 "Playful saturated" — Duolingo pole: bright saturated greens/blues/orange on
// light surfaces, dark ink on the vivid fills (reads friendlier than white-on-signal
// and keeps every pair AA).
val PlayfulLightColorScheme = lightColorScheme(
    primary = Color(0xFF58CC02),
    onPrimary = Color(0xFF1F3A0D),
    primaryContainer = Color(0xFFC6F58F),
    onPrimaryContainer = Color(0xFF1D3A00),
    secondary = Color(0xFF1CB0F6),
    onSecondary = Color(0xFF0B2A3B),
    secondaryContainer = Color(0xFFD5EEFB),
    onSecondaryContainer = Color(0xFF0A3141),
    tertiary = Color(0xFFFF9600),
    onTertiary = Color(0xFF3D2500),
    tertiaryContainer = Color(0xFFFFE3BF),
    onTertiaryContainer = Color(0xFF4F2E00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF33363D),
    surfaceVariant = Color(0xFFE9EFDE),
    onSurfaceVariant = Color(0xFF4A4E44),
    outline = Color(0xFF888C80),
    outlineVariant = Color(0xFFD9DCE0),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2F3134),
    inverseOnSurface = Color(0xFFF0F2EF),
    inversePrimary = Color(0xFF5ED413),
    surfaceDim = Color(0xFFE0E2DC),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F8F4),
    surfaceContainer = Color(0xFFF1F3EE),
    surfaceContainerHigh = Color(0xFFEBEEE8),
    surfaceContainerHighest = Color(0xFFE4E8E1),
)
