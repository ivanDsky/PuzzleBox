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
 * Warning role (ColorTokens.kt): the border and text of something allowed but worth a second
 * look, such as the Strands word field for a word outside the dictionary. Reads 3:1 as a
 * border and 4.5:1 as text on every surface in both modes (ColorTokensTest). Provided by
 * [PuzzleBoxTheme] like [LocalTimerFill].
 */
val LocalWarning = staticCompositionLocalOf { LightWarning }

/**
 * LinkedIn blue as text and outline (ColorTokens.kt): the Resume label and border. Reads 4.5:1 on every surface in
 * both modes (ColorTokensTest). Provided by [PuzzleBoxTheme] like [LocalWarning].
 */
val LocalLinkedInText = staticCompositionLocalOf { LightLinkedInText }

/**
 * The neutral GameButton's lip (ColorTokens.kt). Provided by [PuzzleBoxTheme] like
 * [LocalOnFill].
 */
internal val LocalNeutralLip = staticCompositionLocalOf { LightNeutralLip }

/**
 * Muted-status chip role (MutedStatusColors.kt): a state that is over, like a puzzle taken
 * down. Provided by [PuzzleBoxTheme] like [LocalOnFill].
 */
val LocalMutedStatus = staticCompositionLocalOf { LightMutedStatus }

/**
 * Ice accent role (IceColors.kt): a day a streak freeze covers. Provided by [PuzzleBoxTheme]
 * like [LocalOnFill].
 */
val LocalIce = staticCompositionLocalOf { LightIce }

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
 * The RESOLVED app theme — [PuzzleBoxTheme]'s `darkTheme`, which Settings > Appearance can
 * force opposite the device night flag. Read this, never `isSystemInDarkTheme()` or the
 * configuration's uiMode, when picking theme-specific colours (e.g. a module-local palette
 * keyed on light/dark).
 */
val LocalDarkTheme = staticCompositionLocalOf { false }

/**
 * Theme composition root (ticket 10): the promoted style IS the token set — Baloo 2
 * typography, C3 light scheme, C6 dark scheme. The [darkTheme] request picks the
 * scheme. */
@Composable
fun PuzzleBoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalPrimaryText provides if (darkTheme) DarkPrimaryText else LightPrimaryText,
        LocalTimerFill provides if (darkTheme) DarkTimerFill else LightTimerFill,
        LocalOnFill provides if (darkTheme) DarkOnFill else LightOnFill,
        LocalWarning provides if (darkTheme) DarkWarning else LightWarning,
        LocalLinkedInText provides if (darkTheme) DarkLinkedInText else LightLinkedInText,
        LocalNeutralLip provides if (darkTheme) DarkNeutralLip else LightNeutralLip,
        LocalMutedStatus provides if (darkTheme) DarkMutedStatus else LightMutedStatus,
        LocalIce provides if (darkTheme) DarkIce else LightIce,
        LocalBoardColors provides if (darkTheme) DarkBoardTokens else LightBoardTokens,
        LocalAccentPalette provides accentPalette(darkTheme),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
