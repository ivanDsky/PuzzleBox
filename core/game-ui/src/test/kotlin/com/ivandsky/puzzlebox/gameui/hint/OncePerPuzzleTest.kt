package com.ivandsky.puzzlebox.gameui.hint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** ADR-0025 decision 3: the solution is resolved once per puzzle; a different puzzle resolves its own. */
class OncePerPuzzleTest {

    private data class Puzzle(val id: Int)

    private var calls = 0

    @Test
    fun `the same puzzle computes once however often it is asked`() {
        // Fresh Puzzle(3) each call is deliberate: equal but not identical, so the memo must compare by equality.
        val memo = OncePerPuzzle<Puzzle, Int> { calls++; it.id * 10 }

        assertEquals(30, memo.get(Puzzle(3)))
        assertEquals(30, memo.get(Puzzle(3)))
        assertEquals(30, memo.get(Puzzle(3)))

        assertEquals(1, calls)
    }

    @Test
    fun `a new puzzle computes its own value`() {
        val memo = OncePerPuzzle<Puzzle, Int> { calls++; it.id * 10 }

        assertEquals(10, memo.get(Puzzle(1)))
        assertEquals(20, memo.get(Puzzle(2)))

        assertEquals(2, calls)
    }

    @Test
    fun `only the last puzzle is kept, so returning to an earlier one recomputes`() {
        val memo = OncePerPuzzle<Puzzle, Int> { calls++; it.id }

        memo.get(Puzzle(1))
        memo.get(Puzzle(2))
        memo.get(Puzzle(1))

        assertEquals(3, calls)
    }

    @Test
    fun `a null result is a resolved answer, not a miss`() {
        val memo = OncePerPuzzle<Puzzle, Int?> { calls++; null }

        assertNull(memo.get(Puzzle(1)))
        assertNull(memo.get(Puzzle(1)))

        assertEquals(1, calls)
    }
}
