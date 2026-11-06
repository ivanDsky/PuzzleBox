package com.ivandsky.puzzlebox.games.queensui

/**
 * Spec A9: the why-sentence for each hint-ladder rung, keyed by the rung's own
 * [com.ivandsky.puzzlebox.game.editor.LadderRung.captionKey] — no rung is listed in a `when`.
 * A new rung adds one entry here; QueensCopyCoverageTest fails while it is missing.
 */
object QueensRungCopy {

    private val captions: Map<String, String> = mapOf(
        "queens.hint.rung.singles" to "Every other square of its row, column or color is blocked.",
        "queens.hint.rung.line-lock" to "One color is locked to a single row or column, so no other color fits there.",
        "queens.hint.rung.pigeonhole" to "Some colors fill the same rows or columns between them, so no other color fits there.",
        "queens.hint.rung.look-ahead" to "A queen anywhere else would leave a color no room.",
    )

    /** Caption for a rung's captionKey; null when missing (QueensCopyCoverageTest fails on null). */
    fun caption(captionKey: String): String? = captions[captionKey]
}
