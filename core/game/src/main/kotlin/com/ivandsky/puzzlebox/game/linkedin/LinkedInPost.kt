package com.ivandsky.puzzlebox.game.linkedin

/**
 * Everything one post needs. [gamePath] is the game's LinkedIn route, e.g. `games/zip/` (LinkedInEdition.path).
 * [timeMs] is PuzzleBox active play time.
 */
data class LinkedInPost(
    val gamePath: String,
    val gameTypeId: Int,
    val puzzleNumber: Int,
    val timeMs: Long,
    val hintUsed: Boolean,
    val play: LinkedInPlay,
) {
    /** A13: flawless = no hint and a mistake-free play. The one place this rule lives. */
    val flawless: Boolean get() = play.mistakeFree && !hintUsed
}
