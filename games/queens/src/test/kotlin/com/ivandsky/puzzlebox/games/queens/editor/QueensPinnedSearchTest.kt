package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.games.queens.QueensGenerator
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.solve.QueensSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

/** [QueensPinnedSearch] against the engine's exhaustive [QueensSearch] on boards with one, several and no answer. */
class QueensPinnedSearchTest {

    @Test
    fun `it finds exactly the answers the engine search finds`() {
        for ((name, board) in boards) {
            val engine = QueensSearch.solutions(board, EVERY).map { answer -> answer.map { it.idx }.sorted() }

            val found = QueensPinnedSearch(board.size, Budget.Unlimited).answers(board.regions.toIntArray(), EVERY)

            assertEquals(name, engine.toSet(), found?.toSet())
            assertEquals("$name: an answer came twice", engine.size, found?.size)
        }
    }

    @Test
    fun `the corpus holds boards with one, several and no answer`() {
        val counts = boards.map { (_, board) -> QueensSearch.solutions(board, limit = 2).size }.toSet()

        assertEquals(setOf(0, 1, 2), counts)
    }

    @Test
    fun `it stops at the limit`() {
        val found = QueensPinnedSearch(6, Budget.Unlimited).answers(columns(6), limit = 2)

        assertEquals(2, found?.size)
    }

    @Test
    fun `a spent budget has no answer`() {
        assertNull(QueensPinnedSearch(6, Budget { true }).answers(columns(6), limit = 2))
    }

    private companion object {
        /** More than any corpus board has answers. */
        const val EVERY = 10_000

        /** Regions mirror the columns: every placement that keeps the queens apart is an answer. */
        fun columns(size: Int): IntArray = IntArray(size * size) { it % size }

        /** Generated unique boards of sizes 4..9, each also with six cells repainted at random, and the column boards. */
        val boards: List<Pair<String, QueensPuzzle>> by lazy {
            val generated = (4..9).flatMap { size -> listOf(size * 10, size * 10 + 1) }.flatMap { seed ->
                val size = seed / 10
                val board = checkNotNull(QueensGenerator(Random(seed)).nextUnique(size)) { "no board for seed $seed" }
                val random = Random(seed)
                val repainted = board.regions.toMutableList()
                repeat(6) { repainted[random.nextInt(size * size)] = random.nextInt(size) }
                listOf("n$size seed $seed" to board, "n$size seed $seed repainted" to QueensPuzzle(size, repainted))
            }
            generated + (5..7).map { size -> "n$size columns" to QueensPuzzle(size, columns(size).toList()) }
        }
    }
}
