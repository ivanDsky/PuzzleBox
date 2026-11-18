package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.model.GameId

/** What a game's rules sheet says: [rules] is the how-to-play card's own lines, [controls] how to work the board. */
internal data class RulesSheetCopy(val rules: List<String>, val controls: List<String>)

/**
 * The sheet's copy for [game], or null when the game has no rules to show (classic Sudoku, an unknown id). The rules
 * come from [gameRules] so the card and the sheet cannot drift; controls are keyed by id because play never names a
 * game's module (ADR-0001). A new game is one new entry in [Controls].
 */
internal fun rulesSheetCopy(game: GameId): RulesSheetCopy? {
    val rules = gameRules(game).ifEmpty { return null }
    return RulesSheetCopy(rules, Controls[game.value].orEmpty())
}

private val Controls: Map<String, List<String>> = mapOf(
    "zip" to listOf(
        "Drag from 1 to draw. Lifting your finger keeps the line.",
        "Tap a square on the line to erase back to it. Tap the first square to erase it all.",
    ),
    "tango" to listOf(
        "Tap a third time to clear a square. Squares that start filled can't change.",
    ),
    "queens" to listOf(
        "Tap a third time to clear a square. Drag across squares to fill them with ×.",
        "Auto-cross, in the ⋮ menu at the top, marks the squares each queen rules out.",
    ),
    "strands" to listOf(
        "Tap the last letter again to submit, or drag across the letters.",
        "Words need 4 or more letters. Real words outside the theme count toward hints.",
    ),
    "minisudoku" to listOf(
        "Tap a square, then a number to fill it. Erase empties it.",
        "With Notes on, numbers become small marks instead.",
    ),
)
