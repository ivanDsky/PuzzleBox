package com.ivandsky.puzzlebox.play

/**
 * The archive stat as a plain sentence: how many solves came from the archive, and that they
 * count toward the best time but not the streak. Plain words, no shorthand, so it reads without a legend.
 */
internal fun archiveStatSentence(solves: Int): String {
    val noun = if (solves == 1) "1 solve" else "${count(solves)} solves"
    return "From the archive: $noun. They count toward your best time, not your streak."
}
