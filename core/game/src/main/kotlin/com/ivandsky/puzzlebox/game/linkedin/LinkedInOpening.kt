package com.ivandsky.puzzlebox.game.linkedin

/**
 * The play LinkedIn's web client reports on the first move of a board, before the win: [states] are the game's own
 * states at that point (Zip: the first two path cells and no backtracks), under the same keys and payload arguments
 * as the win's [LinkedInPlay]. It is mistake-free and hint-free by construction, and [timeMs] is at least 1 ms
 * (Zip reports a whole second, Queens between 1 and 1000 ms, Tango at least 1 ms).
 */
data class LinkedInOpening(
    val states: Map<String, LinkedInPlayValue>,
    val timeMs: Long,
)
