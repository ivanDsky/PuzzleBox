package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.graphics.Color

// The readable board look (nav-rethink B3): one set of board tokens all five games share, supplied
// through LocalBoardColors when PuzzleBoxTheme's readableBoard Boolean is on. Same surface as the
// legacy instance; what changes is the relationship between its strokes. Walls take the full board
// ink so they read as walls, the grid line steps down to just above its 2:1 floor so it stays quiet,
// the next number's ring takes the trace blue, and legal cells carry no tint. Everything else is
// today's legacy value, so a game that has not adopted these roles looks the same.
// Ratios vs boardSurface (BoardTokensTest pins the floors): dark wall 12.4, ring 7.4, grid line 2.3;
// light wall 12.1, ring 4.7, grid line 2.2.

val ReadableDarkBoardTokens = DarkBoardTokens.copy(
    boardGridLine = Color(0xFF505863),
    boardWall = DarkBoardTokens.boardInk,
    boardTarget = DarkBoardTokens.pathTrace,
    legalCellTint = Color.Transparent,
)

val ReadableLightBoardTokens = LightBoardTokens.copy(
    boardGridLine = Color(0xFFA8AEB8),
    boardWall = LightBoardTokens.boardInk,
    boardTarget = LightBoardTokens.waypointDotOnPath,
    legalCellTint = Color.Transparent,
)
