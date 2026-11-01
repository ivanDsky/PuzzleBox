package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Board roles (spec UIUX-01 A5/A10): the board's own named color + stroke
// vocabulary, deliberately OUTSIDE the Material colorScheme — the board has no
// Material slots for gridlines, walls, or waypoint dots, and A5 forbids ad-hoc
// colors in composables, so every board consumer reads these roles, never raw
// colors. Dark is the first-class look (the primary user plays a dark-mode
// tablet); light sits alongside with the same role names.
//
// VALUES are research U2's primary table (`.scratch/puzzlebox/research/
// uiux-01-board-palette.md`), adopted by ticket 20: reuse roles alias the live
// ColorTokens.kt values so scheme re-tunes propagate; only three light values are
// new (#0F78B4 on-path dot, #1899D6 trace, #D07C00 selection — darkest-hue-family
// steps chosen as the brightest that clears the bar). Bars: labels & dot fills
// >= 4.5:1 vs boardSurface and label-on-its-fill (WCAG 1.4.3); trace/wall/
// selection >= 3:1 (1.4.11 non-text); gridline >= 2:1 self-imposed "clearly
// visible" floor. BoardTokensTest pins all of them.
//
// The labels carry their own per-fill on-colors: waypointLabel on the green
// waypointDot (onPrimary ink), onPathLabel on the trace-blue waypointDotOnPath
// (onSecondary ink). No single ink can hold 4.5:1 against both a dot fill and the
// surface in dark mode — U2 resolves that by hue-separating the on-path state
// instead of bending one label value across both.

data class BoardColors(
    val boardSurface: Color,
    val boardGridLine: Color,
    val boardWall: Color,
    val waypointDot: Color,
    val waypointLabel: Color,
    val waypointDotOnPath: Color,
    val onPathLabel: Color,
    val pathTrace: Color,
    val boardSelection: Color,
    val boardRegion: Color,
)

// Dark values alias the dark scheme (provenance per U2's table; ratios vs
// boardSurface): gridline 4.59, wall 8.01, dot 9.70, label-on-dot 8.28,
// on-path dot 6.84, onPathLabel-on-fill 6.02, trace 6.84, selection 10.76.
val DarkBoardTokens = BoardColors(
    boardSurface = DarkColorScheme.surfaceContainer,
    boardGridLine = DarkColorScheme.outline,
    boardWall = DarkColorScheme.onSurfaceVariant,
    waypointDot = DarkColorScheme.primary,
    waypointLabel = DarkColorScheme.onPrimary,
    waypointDotOnPath = DarkColorScheme.secondary,
    onPathLabel = DarkColorScheme.onSecondary,
    pathTrace = DarkColorScheme.secondary, // = waypointDotOnPath by design: continuity of the passed path
    boardSelection = DarkColorScheme.tertiary,
    boardRegion = DarkColorScheme.surfaceContainerHigh, // future fill tint; stroke its boundary with wall/gridline when built
)

// Light mirrors dark role-for-role: reuses plus the three new darker steps (dot
// fill #58CC02 fails 4.5 on white at 2.09 — the dot is #377000, the existing
// derived green step). Ratios vs boardSurface: gridline 3.33, wall 8.90, dot 6.03,
// label-on-dot 6.03, on-path dot 4.81, onPathLabel-on-fill 4.81, trace 3.20,
// selection 3.20.
val LightBoardTokens = BoardColors(
    boardSurface = LightColorScheme.surface,
    boardGridLine = LightColorScheme.outline,
    boardWall = LightColorScheme.onSurfaceVariant,
    waypointDot = LightPrimaryText,
    waypointLabel = LightColorScheme.onError, // white
    waypointDotOnPath = Color(0xFF0F78B4), // new: brightest same-hue step of secondary #1CB0F6 clearing 4.5 on white
    onPathLabel = LightColorScheme.onError, // white
    pathTrace = Color(0xFF1899D6), // new: #1CB0F6 itself is 2.44 on white; this clears the 3:1 non-text bar
    boardSelection = Color(0xFFD07C00), // new: tertiary #FF9600 is 2.18 on white; this clears 3:1
    boardRegion = LightColorScheme.surfaceContainerHigh, // future fill tint
)

object BoardTokens {
    // A10: stroke weights are tokens. The 1.5dp gridline floor exists because 1dp
    // hairlines were the original complaint ("borders invisible on the dark
    // palette"); the wall is 4x the gridline so boundaries read at a glance —
    // doubling U2's brighter wall values with a clearly heavier stroke.
    // U1's LinkedIn reference numbers may re-tune both — through these names.
    val boardGridLineWeight: Dp = 1.5.dp
    val boardWallWeight: Dp = 6.dp
}
