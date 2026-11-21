package com.ivandsky.puzzlebox.game.trainer

/**
 * Which part of a technique's chapter a practice board is (spec §2.2). A chapter is four [Focus] boards and then
 * one [Review] board; the first technique of a game's catalog has the four [Focus] boards only. "Learned" means the
 * trained technique and every technique before it in the catalog (easiest first).
 */
enum class BoardKind {
    /**
     * Opens at the start of the puzzle's longest run of walk points where the trained technique is the easiest
     * rung. Every move it asks for is the trained technique. It is done once the easiest rung is any other
     * technique, or the puzzle is solved.
     */
    Focus,

    /**
     * Opens at the first trained point of the run of learned walk points with the most trained points, so most
     * moves are the trained technique and the rest are learned ones. It is done once the easiest rung is a
     * technique later in the catalog than the trained one, or the puzzle is solved.
     */
    Review,
}
