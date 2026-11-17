package com.ivandsky.puzzlebox.play

/**
 * The archive stat as a plain sentence: how many solves came from the archive, and that they
 * count toward the best but never the streak. Null with no archive solve, so the card shows no
 * sentence. Plain words, no shorthand, so it reads without a legend.
 */
internal fun archiveStatSentence(solves: Int): String? {
    if (solves <= 0) return null
    return if (solves == 1) {
        "From the archive: 1 solve. It counts toward your best, never your streak."
    } else {
        "From the archive: ${count(solves)} solves. They count toward your best, never your streak."
    }
}
