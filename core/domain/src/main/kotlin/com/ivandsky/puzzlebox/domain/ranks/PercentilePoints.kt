package com.ivandsky.puzzlebox.domain.ranks

/** Board size at which a leaderboard read is cut off (A11); a player past it has no exact rank. */
internal const val BOARD_PAGE_SIZE = 100

/** Overall points for [rank] on a board of [n] entries: 100 for first, 100/n for last (A9). */
fun percentilePoints(rank: Int, n: Int): Double = 100.0 * (n - rank + 1) / n

/**
 * Points for a player known to be past the read page of a board of [n] entries: the points
 * of the first rank off the page, an upper bound of their true points (A11).
 */
fun pointsBeyondPage(n: Int): Double = 100.0 * (n - BOARD_PAGE_SIZE) / n
