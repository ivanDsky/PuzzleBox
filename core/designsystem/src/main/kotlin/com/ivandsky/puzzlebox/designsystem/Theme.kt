package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Theme composition root (ticket 10): the promoted style IS the token set — Baloo 2
 * typography, C3 light scheme, C6 dark scheme. The [darkTheme] request picks the
 * scheme; no variant parameters remain.
 */
@Composable
fun PuzzleBoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
