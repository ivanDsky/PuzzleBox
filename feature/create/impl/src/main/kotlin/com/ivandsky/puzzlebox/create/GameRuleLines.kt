package com.ivandsky.puzzlebox.create

import com.ivandsky.puzzlebox.model.GameId

/**
 * Each game's one-line rule under its name in the New puzzle sheet and the empty state (01
 * handoff, "New puzzle sheet"): what a board of that game must be. Keyed by id, like play's
 * how-to-play lines, because create never names a game's module (ADR-0019); a game not listed
 * here shows its name and default size only.
 */
internal fun gameRuleLine(game: GameId): String? = RuleLines[game.value]

private val RuleLines: Map<String, String> = mapOf(
    "zip" to "Draw one path through every cell, hitting the numbers in order",
    "queens" to "One crown in every row, column and colour region",
    "tango" to "Fill every cell with a sun or moon; = and × link neighbours",
    "minisudoku" to "1 to 6 once in every row, column and 2×3 box",
    "strands" to "Hide a theme’s words and one spangram in 8×6 letters",
    "sudoku" to "Debug build only — same engine, 3×3 boxes",
)
