package com.ivandsky.puzzlebox.gameui

/** Which way a trace's live tip leans from the tail (spec A4). */
enum class TipKind {
    /** Toward a cell the game would accept: the line end follows the finger. */
    Forward,

    /** Toward the previous cell: the line shortens under the finger. */
    Back,

    /** Anywhere the trace cannot grow (a wall, the edge, a used cell, a tail the finger has not reached): a short nudge, never a stretch. */
    Nudge,
}
