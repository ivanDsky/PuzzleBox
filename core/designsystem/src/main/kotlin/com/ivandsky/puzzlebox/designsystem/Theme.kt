package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Text-role step of the primary hue (see ColorTokens.kt): AppTextButton and any
 * component that renders the primary color as text reads this instead of
 * `colorScheme.primary`, whose light value is a fill green that fails AA as text.
 * Provided by [PuzzleBoxTheme] so it always follows the RESOLVED theme, including a
 * forced appearance opposite the system.
 */
val LocalPrimaryText = staticCompositionLocalOf { LightPrimaryText }

/**
 * Timer bar fill role (ColorTokens.kt, spec A8): the progress fill reads this instead of
 * `colorScheme.primary`, whose light value holds under 3:1 against the
 * `surfaceVariant` track. Provided by [PuzzleBoxTheme] like [LocalPrimaryText].
 */
val LocalTimerFill = staticCompositionLocalOf { LightTimerFill }

/**
 * Content colour on a full accent fill (ColorTokens.kt): hub card text, status marks and
 * motif read this. Same for every accent slot. Provided by [PuzzleBoxTheme] like
 * [LocalTimerFill].
 */
val LocalOnFill = staticCompositionLocalOf { LightOnFill }

/**
 * Board color roles (BoardTokens.kt, spec UIUX-01 A5): the board renders from these named
 * roles, never raw colors. Provided by [PuzzleBoxTheme] so board rendering always follows
 * the RESOLVED theme — same contract as [LocalPrimaryText], including a forced appearance
 * opposite the system.
 */
val LocalBoardColors = staticCompositionLocalOf { LightBoardTokens }

/**
 * Game accent slots (AccentTokens.kt, spec A1): [AccentSlot.colors] resolves a slot
 * through this. Provided by [PuzzleBoxTheme] like [LocalBoardColors], so accents follow
 * the RESOLVED theme, including a forced appearance opposite the system.
 */
val LocalAccentPalette = staticCompositionLocalOf { LightAccentTokens }

/**
 * Theme composition root (ticket 10): the promoted style IS the token set — Baloo 2
 * typography, C3 light scheme, C6 dark scheme. The [darkTheme] request picks the
 * scheme. The debug design settings' [DesignVariantsStore] picks the accent set and is
 * provided as [LocalDesignVariants]; release always reads [DesignVariants.Default].
 */
@Composable
fun PuzzleBoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val variants = DesignVariantsStore.current
    CompositionLocalProvider(
        LocalDesignVariants provides variants,
        LocalPrimaryText provides if (darkTheme) DarkPrimaryText else LightPrimaryText,
        LocalTimerFill provides if (darkTheme) DarkTimerFill else LightTimerFill,
        LocalOnFill provides if (darkTheme) DarkOnFill else LightOnFill,
        LocalBoardColors provides if (darkTheme) DarkBoardTokens else LightBoardTokens,
        LocalAccentPalette provides accentPalette(variants.accentSet, darkTheme),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
