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
// ColorTokens.kt values so scheme re-tunes propagate; light's three new values
// (#0F78B4 on-path dot, #1899D6 trace, #D07C00 selection — darkest-hue-family
// steps chosen as the brightest that clears the bar) and dark's trace pair are
// literals — ticket 27 softened the dark trace off the scheme's neon secondary.
// Bars: labels & dot fills
// >= 4.5:1 vs boardSurface and label-on-its-fill (WCAG 1.4.3); trace/wall/
// selection >= 3:1 (1.4.11 non-text); gridline >= 2:1 self-imposed "clearly
// visible" floor. BoardTokensTest pins all of them.
//
// Labels ride the ring color: hollow waypoints (gate r3/r4) put every label on
// boardSurface in its ring's own color — green 9.70:1 / powder 9.89:1, no
// per-fill on-color needed.

data class BoardColors(
    val boardSurface: Color,
    val boardGridLine: Color,
    val boardWall: Color,
    val waypointDot: Color,
    val waypointLabel: Color,
    val waypointDotOnPath: Color,
    val pathTrace: Color,
    val boardSelection: Color,
    val boardRegion: Color,

    // Tango glyph fills (design-direction §3): sun/moon must clear the 3:1 non-text
    // bar vs boardSurface in BOTH modes; identity rides the silhouette (disc+rays vs
    // crescent), so the fill only has to be unmistakably present. Rejected on purpose
    // (§3): light tertiary #FF9600 (2.18 ✗), light secondary #1CB0F6 (2.44 ✗), light
    // #D07C00 sun (passes but collides with boardSelection light — selected sun cell
    // would read as one orange blob), dark secondary #1CB0F6 moon (passes but is the
    // value ticket 27 retired as neon glare).
    val sunFill: Color,
    val moonFill: Color,

    // Strands (spec A32): the app's brand hues on round letter tokens, read the way the
    // rest of the app reads them — blue while you trace (Zip's live path), green for a found
    // theme word (correct), gold for the spangram (special). The letter on a filled token is
    // always black (>= 4.5:1 on each fill). The hint ring previews its word's green and is
    // non-text (>= 3:1 vs boardSurface); an idle letter sits on the bare board (>= 4.5:1).
    // Connectors take their word's fill.
    val strandsThemeFill: Color,
    val strandsSpangramFill: Color,
    val strandsSelectionFill: Color,
    val strandsHintRing: Color,
    val strandsTileText: Color,
    val strandsIdleLetter: Color,
    val strandsTraceConnector: Color,
    val strandsThemeConnector: Color,
    val strandsSpangramConnector: Color,
    val strandsHintConnector: Color,
)

// Dark mode keeps the softened blue step Zip's trace settled on (T27: scheme #1CB0F6 read
// as neon glare on dark); green and gold are the dark scheme's own primary and tertiary.
private val DarkTracePowder = Color(0xFFB3CBD6) // = DarkBoardTokens.pathTrace, black text 12.3:1

// Dark values alias the dark scheme (provenance per U2's table; ratios vs
// boardSurface): gridline 4.59, wall 8.01, dot 9.70, label-on-dot 8.28,
// selection 10.76. Ticket 27 retuned the shared trace/on-path dot blue off the
// scheme: on-path dot / trace 9.89 — same hue as
// #1CB0F6, lighter + desaturated (user: "some lighter shade of blue") after it
// read as neon glare.
val DarkBoardTokens = BoardColors(
    boardSurface = DarkColorScheme.surfaceContainer,
    boardGridLine = DarkColorScheme.outline,
    boardWall = DarkColorScheme.onSurfaceVariant,
    waypointDot = DarkColorScheme.primary,
    waypointLabel = DarkColorScheme.onPrimary,
    waypointDotOnPath = Color(0xFFB3CBD6), // T27 gate R1/D1: scheme secondary #1CB0F6 read as neon glare — powder step of the SAME hue (HSL 199°, S 92→30%, L 54→77%; oklch C 0.151→0.030), literal no longer aliases
    pathTrace = Color(0xFFB3CBD6), // = waypointDotOnPath by design: continuity of the passed path
    boardSelection = DarkColorScheme.tertiary,
    boardRegion = DarkColorScheme.surfaceContainerHigh, // future fill tint; stroke its boundary with wall/gridline when built
    sunFill = DarkColorScheme.tertiary, // #FFC800, 10.76:1 — dark scheme tertiary already cleared the bar; no new literal
    moonFill = Color(0xFFB3CBD6), // T27 "powder" literal, 9.89:1 — same hue as secondary, proven non-glaring
    strandsThemeFill = DarkColorScheme.primary, // #6BDF17, black text 12.4:1
    strandsSpangramFill = DarkColorScheme.tertiary, // #FFC800, black text 13.4:1
    strandsSelectionFill = DarkTracePowder,
    strandsHintRing = DarkColorScheme.primary,
    strandsTileText = Color.Black,
    strandsIdleLetter = DarkColorScheme.onSurface,
    strandsTraceConnector = DarkTracePowder,
    strandsThemeConnector = DarkColorScheme.primary,
    strandsSpangramConnector = DarkColorScheme.tertiary,
    strandsHintConnector = DarkColorScheme.primary,
)

// Light mirrors dark role-for-role: reuses plus the three new darker steps (dot
// fill #58CC02 fails 4.5 on white at 2.09 — the dot is #377000, the existing
// derived green step). Ratios vs boardSurface: gridline 3.33, wall 8.90, dot 6.03,
// label-on-dot 6.03, on-path dot 4.81, trace 3.20,
// selection 3.20.
val LightBoardTokens = BoardColors(
    boardSurface = LightColorScheme.surface,
    boardGridLine = LightColorScheme.outline,
    boardWall = LightColorScheme.onSurfaceVariant,
    waypointDot = LightPrimaryText,
    waypointLabel = LightColorScheme.onError, // white
    waypointDotOnPath = Color(0xFF0F78B4), // new: brightest same-hue step of secondary #1CB0F6 clearing 4.5 on white
    pathTrace = Color(0xFF1899D6), // new: #1CB0F6 itself is 2.44 on white; this clears the 3:1 non-text bar
    boardSelection = Color(0xFFD07C00), // new: tertiary #FF9600 is 2.18 on white; this clears 3:1
    boardRegion = LightColorScheme.surfaceContainerHigh, // future fill tint
    sunFill = Color(0xFFC25A00), // new: orange family, darkest step clearing 3:1 with selection headroom — 4.42:1 on white
    moonFill = Color(0xFF0F78B4), // existing waypointDotOnPath light literal, blue family — 4.81:1
    strandsThemeFill = LightColorScheme.primary, // #58CC02, black text 10.2:1
    strandsSpangramFill = DarkColorScheme.tertiary, // #FFC800 gold in both modes; light tertiary #FF9600 reads as Tango's sun
    strandsSelectionFill = LightColorScheme.secondary, // #1CB0F6, black text 8.4:1
    strandsHintRing = LightTimerFill, // #3A8200, the green step that clears 3:1 on white (4.8:1)
    strandsTileText = Color.Black,
    strandsIdleLetter = LightColorScheme.onSurface,
    strandsTraceConnector = LightColorScheme.secondary,
    strandsThemeConnector = LightColorScheme.primary,
    strandsSpangramConnector = DarkColorScheme.tertiary,
    strandsHintConnector = LightTimerFill,
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
