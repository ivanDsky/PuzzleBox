package com.ivandsky.puzzlebox.gameui

/**
 * The in-play message seam beside [PlayHints] (spec A7/A18): the game owns what its
 * messages say; the chrome's one caption slot shows them. [violation] is the standing
 * rule break on the board (null = legal), [rejection] the last refused input (null =
 * none). Both are plain copy, ready to read aloud.
 */
interface PlayFeedback {
    val violation: String?
    val rejection: String?
}

/**
 * The caption the shared slot shows, resolved once for the chrome and the tutorial host:
 * violation > rejection > hint explanation. Null = the slot stays empty (its height stays
 * reserved either way).
 */
fun playCaption(feedback: PlayFeedback?, hints: PlayHints?): String? =
    feedback?.violation ?: feedback?.rejection ?: hints?.hintCaption
