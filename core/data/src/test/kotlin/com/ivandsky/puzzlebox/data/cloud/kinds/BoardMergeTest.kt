package com.ivandsky.puzzlebox.data.cloud.kinds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** The merge table of [BoardMerge]: whole boards, completeness never downgrades, linked profile first (A19, A25). */
class BoardMergeTest {

    private fun board(
        key: String = "aaaaaaaaaaaaaaaa",
        readAtMs: Long = 1_000L,
        complete: Boolean = false,
        name: String = "Ann",
    ) = BoardValue(
        game = "queens",
        day = "2026-11-10",
        profileKey = key,
        readAtMs = readAtMs,
        complete = complete,
        rows = listOf(BoardRow(name, 61_000L, hintFree = true, flawless = false, isOwn = true)),
    )

    @Test
    fun `a board with no rival wins`() {
        val only = board()
        assertSame(only, BoardMerge.winner(listOf(only), linkedKey = null))
    }

    @Test
    fun `a complete board is never replaced by a partial one, however new`() {
        val complete = board(readAtMs = 1_000L, complete = true, name = "final")
        val partial = board(readAtMs = 9_000L, complete = false, name = "partial")
        assertSame(complete, BoardMerge.winner(listOf(complete, partial), null))
        assertSame(complete, BoardMerge.winner(listOf(partial, complete), null))
    }

    @Test
    fun `a complete board replaces a partial one even when it was read earlier`() {
        val partial = board(readAtMs = 9_000L, complete = false)
        val complete = board(readAtMs = 1_000L, complete = true)
        assertSame(complete, BoardMerge.winner(listOf(partial, complete), null))
    }

    @Test
    fun `of two boards of the same completeness the newer read wins`() {
        for (complete in listOf(true, false)) {
            val older = board(readAtMs = 1_000L, complete = complete, name = "older")
            val newer = board(readAtMs = 2_000L, complete = complete, name = "newer")
            assertSame(newer, BoardMerge.winner(listOf(older, newer), null))
            assertSame(newer, BoardMerge.winner(listOf(newer, older), null))
        }
    }

    @Test
    fun `a board merges whole, a newer board never borrows rows from the older one`() {
        val older = board(readAtMs = 1_000L).copy(rows = listOf(BoardRow("Bo", 50_000L, true, false, false)))
        val newer = board(readAtMs = 2_000L).copy(rows = listOf(BoardRow("Bo", null, true, false, false)))
        assertEquals(listOf(BoardRow("Bo", null, true, false, false)), BoardMerge.winner(listOf(older, newer), null).rows)
    }

    @Test
    fun `merging the same board again keeps the local one`() {
        val local = board(complete = true)
        val again = local.copy(rows = local.rows.reversed())
        assertSame(local, BoardMerge.winner(listOf(local, again), null))
    }

    @Test
    fun `the linked profile's board wins over a newer one of another profile`() {
        val linked = board(key = "1111111111111111", readAtMs = 1_000L, complete = false)
        val other = board(key = "2222222222222222", readAtMs = 9_000L, complete = true)
        assertSame(linked, BoardMerge.winner(listOf(other, linked), linkedKey = "1111111111111111"))
        assertSame(linked, BoardMerge.winner(listOf(linked, other), linkedKey = "1111111111111111"))
    }

    @Test
    fun `without a linked board the latest read wins across profiles`() {
        val older = board(key = "1111111111111111", readAtMs = 1_000L)
        val newer = board(key = "2222222222222222", readAtMs = 2_000L)
        assertSame(newer, BoardMerge.winner(listOf(older, newer), linkedKey = null))
        assertSame(newer, BoardMerge.winner(listOf(older, newer), linkedKey = "3333333333333333"))
        assertSame(newer, BoardMerge.winner(listOf(newer, older), linkedKey = null))
    }

    @Test
    fun `the outcome does not depend on the order of the boards`() {
        val boards = listOf(
            board(key = "1111111111111111", readAtMs = 1_000L, complete = true),
            board(key = "1111111111111111", readAtMs = 5_000L, complete = false),
            board(key = "2222222222222222", readAtMs = 3_000L, complete = false),
            board(key = "3333333333333333", readAtMs = 2_000L, complete = true),
        )
        for (linked in listOf(null, "1111111111111111", "2222222222222222")) {
            val outcomes = permutations(boards).map { BoardMerge.winner(it, linked).fingerprint() }.toSet()
            assertEquals("linked=$linked", 1, outcomes.size)
        }
    }

    @Test
    fun `a rival profile's later partial read does not beat the best board of the linked profile`() {
        val linkedComplete = board(key = "1111111111111111", readAtMs = 1_000L, complete = true)
        val linkedPartial = board(key = "1111111111111111", readAtMs = 8_000L, complete = false)
        val other = board(key = "2222222222222222", readAtMs = 9_000L)
        val winner = BoardMerge.winner(listOf(linkedPartial, other, linkedComplete), linkedKey = "1111111111111111")
        assertSame(linkedComplete, winner)
    }

    private fun <T> permutations(items: List<T>): List<List<T>> =
        if (items.size <= 1) listOf(items) else items.indices.flatMap { i ->
            permutations(items.filterIndexed { j, _ -> j != i }).map { listOf(items[i]) + it }
        }
}
