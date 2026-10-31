package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Theme composition root. The variant parameters are the ticket 08 debug style
 * prototypes: defaults resolve to the exact shipped token objects, so the production
 * path (no variant arguments) renders byte-identically to the pre-variant build.
 */
@Composable
fun PuzzleBoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    typographyVariant: TypographyVariant = TypographyVariant.DEFAULT,
    paletteVariant: PaletteVariant = PaletteVariant.DEFAULT,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = paletteVariant.toColorScheme(darkTheme),
        typography = typographyVariant.toTypography(),
        shapes = AppShapes,
        content = content,
    )
}
