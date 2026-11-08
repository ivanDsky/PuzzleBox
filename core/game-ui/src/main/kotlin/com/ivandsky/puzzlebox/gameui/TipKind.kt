package com.ivandsky.puzzlebox.gameui

/** Which way a trace's live tip leans from the tail (spec A4): at a cell the game would take, or back along the path. */
enum class TipKind {
    /** Toward a cell the game would accept as the next one: the line end follows the finger. */
    Forward,

    /** Toward the previous cell: the line shortens under the finger. */
    Back,
}
