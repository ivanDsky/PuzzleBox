package com.ivandsky.puzzlebox.game.editor

/** Capability (ADR-0019 Consequences): the game reads and writes `puzzlebox <game> v<n>` text (spec A23). */
interface EditorNotation<D : Any> {
    /** Highest notation version this build reads and the one [print] writes (ADR-0005). */
    val notationVersion: Int

    /**
     * [text] is the whole input with its header line and every `#` comment line blanked to "",
     * so NotationResult.Err line/col are whole-text, 1-based. Blank lines are insignificant in
     * every game's grammar. The board is described without its solution.
     */
    fun parse(text: String): NotationResult<D>

    /** Whole canonical text: `NotationHeader(gameId.value, notationVersion).line`, then the body; ends with "\n". */
    fun print(draft: D): String

    /** Cell-grid width, so cell indices map to 1-based (row, col) = (i / columns + 1, i % columns + 1). */
    fun columns(draft: D): Int
}
