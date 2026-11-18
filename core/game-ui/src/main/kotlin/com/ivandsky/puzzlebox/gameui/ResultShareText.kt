package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.designsystem.formatDuration

/**
 * The result's share text. Line 1 is the unchanged header, "PuzzleBox Queens #918 · 0:58 · no hints" ([number] is the
 * edition number; a practice puzzle (none) leaves it out). The [strip] line ([solveStrip]) is optional and shows
 * effort, never the board, so the text never spoils the puzzle. The [link] line is optional. Lines are joined with
 * `\n`; with both null the text is the header alone.
 */
fun resultShareText(
    game: String,
    number: Int?,
    timeMs: Long,
    hints: Int,
    link: String? = null,
    strip: String? = null,
): String {
    val puzzle = number?.let { "$game #$it" } ?: game
    val help = when (hints) {
        0 -> "no hints"
        1 -> "1 hint"
        else -> "$hints hints"
    }
    return listOfNotNull("PuzzleBox $puzzle · ${formatDuration(timeMs)} · $help", strip, link).joinToString("\n")
}
