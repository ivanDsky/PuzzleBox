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
// Surfaces, gridline, wall and the unvisited waypoint are research U2's table
// (`.scratch/puzzlebox/research/uiux-01-board-palette.md`) aliasing the scheme.
// Every game highlight takes its own game's Splash accent (spec A14, prototype
// 03-game-colors ?v=d): dark uses the accent itself; light uses the brightest
// gamut-edge step of the same hue that clears its bar on white. `*Lip` roles are
// the chunky-depth bottom edge: same hue, OKLCH L −0.13 light / −0.20 dark.
// Bars: labels & dot fills >= 4.5:1 vs boardSurface and label-on-its-fill
// (WCAG 1.4.3); trace/wall/selection >= 3:1 (1.4.11 non-text); gridline >= 2:1
// self-imposed "clearly visible" floor. BoardTokensTest pins all of them.
//
// Labels ride the ring color: hollow waypoints put every label on boardSurface in
// its ring's own color, so the light on-path ring is the text-grade blue, not the trace.

data class BoardColors(
    val boardSurface: Color,
    val boardGridLine: Color,
    val boardWall: Color,
    val waypointDot: Color,
    val waypointLabel: Color,
    val waypointDotOnPath: Color,
    val pathTrace: Color,
    val pathTraceLip: Color,
    // Queens' hint edge and overlay: the Queens violet.
    val boardSelection: Color,
    val boardRegion: Color,

    // Tango glyph fills: sun/moon clear the 3:1 non-text bar vs boardSurface and vs the
    // hint tile in both modes; identity rides the silhouette (disc+rays vs crescent).
    val sunFill: Color,
    val moonFill: Color,
    // The hinted square: the Tango orange tint as a tile on its lip.
    val tangoHintFill: Color,
    val tangoHintLip: Color,

    // Mini Sudoku: the selected cell is the full teal fill with the hue's deep ink on
    // it; its row/column/box peers sit on the teal tint.
    val sudokuSelection: Color,
    val sudokuSelectionInk: Color,
    val sudokuSelectionLip: Color,
    val sudokuPeer: Color,

    // Strands (spec A32, A14): found theme words alternate pink and magenta, the
    // spangram is gold, the live trace hot pink. The letter on a filled token is
    // always black (>= 4.5:1 on each fill). The hint ring previews the pink and is
    // non-text (>= 3:1 vs boardSurface); an idle letter sits on the bare board
    // (>= 4.5:1). Connectors take their word's fill. Each filled token sits on its lip.
    val strandsThemeFill: Color,
    val strandsThemeAltFill: Color,
    val strandsSpangramFill: Color,
    val strandsSelectionFill: Color,
    val strandsThemeLip: Color,
    val strandsThemeAltLip: Color,
    val strandsSpangramLip: Color,
    val strandsSelectionLip: Color,
    val strandsHintRing: Color,
    val strandsTileText: Color,
    val strandsIdleLetter: Color,
    val strandsTraceConnector: Color,
    val strandsThemeConnector: Color,
    val strandsThemeAltConnector: Color,
    val strandsSpangramConnector: Color,
    val strandsHintConnector: Color,

    // Editor diagnostics (editor spec S19, prototype 03 b): editor-only shading the play
    // board never draws. The "2+ solutions" ring deliberately avoids boardSelection (the
    // play hint tint): a saturated teal, light over dark fills and deep over pastel ones,
    // so the ring reads on any region. The clash fill is a 75% red wash: the scheme error in
    // dark, one tone deeper in light, where the scheme error over the Candy red fill reads
    // 2.5:1 (29: 45% read 2.0:1 on light pastels); a crown on it takes its ink against the
    // washed fill. Split stripes are
    // the play UI's violation stripes, so they take the scheme error as is.
    val editorDiffRing: Color,
    val editorClashFill: Color,
    val editorSplitHatch: Color,
)

private val SplashGold = Color(0xFFFECF00) // spangram in both modes, black text 14.1:1
private val StrandsHotPink = Color(0xFFE40070) // live trace in both modes, black text 4.53:1

// Dark: every highlight is its game's Splash accent; ratios vs boardSurface #1A1E25:
// trace/on-path 7.39, Queens violet 6.9, Strands pink 6.68.
val DarkBoardTokens = BoardColors(
    boardSurface = DarkColorScheme.surfaceContainer,
    boardGridLine = DarkColorScheme.outline,
    boardWall = DarkColorScheme.onSurfaceVariant,
    waypointDot = DarkColorScheme.primary,
    waypointLabel = DarkColorScheme.onPrimary,
    waypointDotOnPath = Color(0xFF21B7FF),
    pathTrace = Color(0xFF21B7FF), // = waypointDotOnPath by design: continuity of the passed path
    pathTraceLip = Color(0xFF0077AA),
    boardSelection = Color(0xFFA69BFF),
    boardRegion = DarkColorScheme.surfaceContainerHigh, // future fill tint; stroke its boundary with wall/gridline when built
    sunFill = Color(0xFFFF8524),
    moonFill = Color(0xFF21B7FF),
    tangoHintFill = Color(0xFF4E270B),
    tangoHintLip = Color(0xFF0F0300), // −0.20 from a dark tint lands near black, like the neutral lip
    sudokuSelection = Color(0xFF00E8DC),
    sudokuSelectionInk = Color(0xFF002E2B),
    sudokuSelectionLip = Color(0xFF00A198),
    sudokuPeer = Color(0xFF003C38),
    strandsThemeFill = Color(0xFFFF77A0),
    strandsThemeAltFill = Color(0xFFFF63E4),
    strandsSpangramFill = SplashGold,
    strandsSelectionFill = StrandsHotPink,
    strandsThemeLip = Color(0xFFB93665),
    strandsThemeAltLip = Color(0xFFB90DA3),
    strandsSpangramLip = Color(0xFFB39100),
    strandsSelectionLip = Color(0xFF83003D),
    strandsHintRing = Color(0xFFFF77A0),
    strandsTileText = Color.Black,
    strandsIdleLetter = DarkColorScheme.onSurface,
    strandsTraceConnector = StrandsHotPink,
    strandsThemeConnector = Color(0xFFFF77A0),
    strandsThemeAltConnector = Color(0xFFFF63E4),
    strandsSpangramConnector = SplashGold,
    strandsHintConnector = Color(0xFFFF77A0),
    editorDiffRing = Color(0xFF7FE3F0), // new: light teal, >= 7:1 over every dark region fill (L* 18–26)
    editorClashFill = DarkColorScheme.error.copy(alpha = 0.75f),
    editorSplitHatch = DarkColorScheme.error,
)

// Light: the accent fills are too pale for strokes on white, so strokes step down the
// same hue — trace 3.05, sun 3.92, moon 3.91, Queens hint 4.00, Strands hint 3.06 —
// and the on-path ring/label is the text-grade blue (4.65).
val LightBoardTokens = BoardColors(
    boardSurface = LightColorScheme.surface,
    boardGridLine = LightColorScheme.outline,
    boardWall = LightColorScheme.onSurfaceVariant,
    waypointDot = LightPrimaryText,
    waypointLabel = LightColorScheme.onError, // white
    waypointDotOnPath = Color(0xFF007CB1),
    pathTrace = Color(0xFF009DDE),
    pathTraceLip = Color(0xFF0074A5),
    boardSelection = Color(0xFF8464FF),
    boardRegion = LightColorScheme.surfaceContainerHigh, // future fill tint
    sunFill = Color(0xFFCB6400),
    moonFill = Color(0xFF0089C3),
    tangoHintFill = Color(0xFFFFDDC9),
    tangoHintLip = Color(0xFFD4B3A0),
    sudokuSelection = Color(0xFF00C8BD),
    sudokuSelectionInk = Color(0xFF002E2B),
    sudokuSelectionLip = Color(0xFF009B92),
    sudokuPeer = Color(0xFFB7F2EC),
    strandsThemeFill = Color(0xFFFF478A),
    strandsThemeAltFill = Color(0xFFFD00DF),
    strandsSpangramFill = SplashGold,
    strandsSelectionFill = StrandsHotPink,
    strandsThemeLip = Color(0xFFCF0064),
    strandsThemeAltLip = Color(0xFFBF00A8),
    strandsSpangramLip = Color(0xFFCDA700),
    strandsSelectionLip = Color(0xFFA4004E),
    strandsHintRing = Color(0xFFFF528F),
    strandsTileText = Color.Black,
    strandsIdleLetter = LightColorScheme.onSurface,
    strandsTraceConnector = StrandsHotPink,
    strandsThemeConnector = Color(0xFFFF478A),
    strandsThemeAltConnector = Color(0xFFFD00DF),
    strandsSpangramConnector = SplashGold,
    strandsHintConnector = Color(0xFFFF528F),
    editorDiffRing = Color(0xFF004F58), // deep teal, >= 4.7:1 over every light Candy fill; >= 3.8:1 as the 85% ghost
    editorClashFill = Color(0xFF93000A).copy(alpha = 0.75f), // error tone 30: >= 3.4:1 washed over every light fill
    editorSplitHatch = LightColorScheme.error,
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
