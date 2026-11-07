package com.ivandsky.puzzlebox.game.editor

/** One deduction technique of a game's hint ladder (spec A9). */
interface LadderRung {
    /** Stable machine id; also the suffix of the caption test tag. */
    val id: String
    val level: Difficulty.Level
    /** Key into the game UI's caption copy. */
    val captionKey: String
}
