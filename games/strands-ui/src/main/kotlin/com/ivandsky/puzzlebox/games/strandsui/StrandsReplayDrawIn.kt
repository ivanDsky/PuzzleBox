package com.ivandsky.puzzlebox.games.strandsui

/**
 * The replay board's draw-in of a submitted word's recorded path, as a pure timeline: one letter per
 * [StrandsMotion.ReplayDrawInLetterMs], so tests read it without a clock. Whatever follows (a non-theme
 * word's set-aside, a found word's settle) starts only once [doneAt] elapsed time has passed.
 */
internal object ReplayDrawIn {
    /** How long the path of [letters] letters takes to draw. */
    fun totalMs(letters: Int): Int = letters * StrandsMotion.ReplayDrawInLetterMs

    /** How many letters of a [letters]-letter path are drawn [elapsedMs] in. */
    fun lettersAt(elapsedMs: Int, letters: Int): Int {
        val step = StrandsMotion.ReplayDrawInLetterMs
        return ((elapsedMs + step - 1) / step).coerceIn(0, letters)
    }

    /** True once every letter is drawn, so the hand-over (set-aside or found animation) may start. */
    fun doneAt(elapsedMs: Int, letters: Int): Boolean = elapsedMs >= totalMs(letters)
}
