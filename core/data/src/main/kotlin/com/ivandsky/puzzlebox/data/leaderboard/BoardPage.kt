package com.ivandsky.puzzlebox.data.leaderboard

/**
 * One board read once: the ranked [entries] (at most [PAGE_SIZE]) and [count], the whole
 * board's N — equal to `entries.size` unless the page is full and the board is longer.
 */
data class BoardPage(val entries: List<LeaderboardEntry>, val count: Int) {
    companion object {
        /** The most entries a board read returns; a page this long may hide a longer board. */
        const val PAGE_SIZE = 100
    }
}
