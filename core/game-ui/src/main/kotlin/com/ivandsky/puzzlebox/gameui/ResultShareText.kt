package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.designsystem.formatDuration

/**
 * The result's share line (spec A17): plain text with no link and nothing about the
 * board, so it never spoils the puzzle — "PuzzleBox Queens #918 · 0:58 · no hints".
 * [number] is the edition number; a practice puzzle (none) leaves it out.
 */
fun resultShareText(game: String, number: Int?, timeMs: Long, hints: Int): String {
    val puzzle = number?.let { "$game #$it" } ?: game
    val help = when (hints) {
        0 -> "no hints"
        1 -> "1 hint"
        else -> "$hints hints"
    }
    return "PuzzleBox $puzzle · ${formatDuration(timeMs)} · $help"
}
