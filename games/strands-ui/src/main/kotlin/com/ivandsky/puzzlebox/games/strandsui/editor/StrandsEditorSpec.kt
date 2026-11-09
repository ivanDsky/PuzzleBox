package com.ivandsky.puzzlebox.games.strandsui.editor

/** The editor's drawn numbers, by name. */
internal object StrandsEditorSpec {

    /**
     * The alpha every unselected word, connector, tile and letter fades to while a path is
     * selected (user F9c). Low enough that the selected word is the only full-strength thing on
     * the board, high enough that a faded letter stays legible in light and dark.
     */
    const val DimAlpha: Float = 0.5f
}
