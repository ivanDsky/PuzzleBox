package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.model.GameId

/**
 * The game page's how-to-play lines (A28): each game's lessons folded into three short
 * rules. Keyed by id because play never names a game's module (ADR-0001); a game not
 * listed here (classic Sudoku is unlisted) has none and its card shows only the lesson.
 */
internal fun gameRules(game: GameId): List<String> = Rules[game.value].orEmpty()

private val Rules: Map<String, List<String>> = mapOf(
    "zip" to listOf(
        "Draw one line from 1 through every number in order",
        "Fill every square on the way",
        "Walls block the line",
    ),
    "tango" to listOf(
        "Tap once for a sun, twice for a moon",
        "Never three suns or moons in a row; each row and column has as many of each",
        "= means the same, × means different",
    ),
    "queens" to listOf(
        "Tap once for ×, twice for a queen",
        "One queen in each row, column and color",
        "Queens never touch, not even corner to corner",
    ),
    "strands" to listOf(
        "Tap letters next to each other to spell a word",
        "Theme words fill the board; the spangram touches two opposite sides",
        "Every 3 other words earn a hint",
    ),
    "minisudoku" to listOf(
        "Each row and column needs every number once",
        "Each box needs every number once, too",
        "Notes are small marks for numbers that might fit",
    ),
)
