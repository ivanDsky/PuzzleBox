package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensTestSupport
import com.ivandsky.puzzlebox.games.queens.codec.QueensWireParser
import com.ivandsky.puzzlebox.games.queens.engine.hints.QueensHint
import com.ivandsky.puzzlebox.games.queens.engine.hints.QueensHints
import com.ivandsky.puzzlebox.games.queens.feed.QueensGameExtractor
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The singles-only ladder (spec A9, ticket 05): it solves the easy boards on its own, its
 * hardest rung labels them Easy, every by-eye daily hint is tagged with [SinglesRung], and
 * its order is deterministic — lowest highlighted region, then lowest cell, then the
 * ROW → COLUMN → REGION proof (the order today's hints use).
 */
class QueensLadderTest {

    private val singlesOnly = QueensLadder(listOf(SinglesRung))

    private fun daily(name: String): QueensPuzzle {
        val body = QueensLadderTest::class.java.getResourceAsStream("/fixtures/$name")!!
            .readBytes().decodeToString()
        val wire = QueensGameExtractor.extract(body)?.queensGamePuzzleJson ?: body
        return when (val r = QueensWireParser.parse(wire, LocalDate.of(2026, 11, 2))) {
            is DecodeResult.Ok -> r.value.puzzle
            is DecodeResult.Err -> throw AssertionError("$name parse failed: ${r.error}")
        }
    }

    /**
     * 5×5 staircase: region 0 is the lone cell 0 and each later region holds its queen plus
     * cells the previous queen blocks, so every queen is a single in turn (solution
     * 0, 7, 14, 16, 23). No committed daily is singles-only: each needs a harder rung.
     */
    private fun staircase5(): QueensPuzzle = QueensPuzzle(
        size = 5,
        regions = listOf(
            0, 1, 1, 1, 1,
            1, 1, 1, 2, 2,
            1, 2, 2, 2, 2,
            1, 3, 2, 3, 3,
            1, 4, 2, 4, 3,
        ),
        solution = QueensTestSupport.path(0, 7, 14, 16, 23),
    )

    private fun easyBoards(): List<Pair<String, QueensPuzzle>> = listOf(
        "n4" to QueensTestSupport.n4Puzzle(),
        "staircase5" to staircase5(),
    )

    private fun dailies(): List<Pair<String, QueensPuzzle>> =
        listOf("queens-daily-graphql.json", "queens-daily-real-916.json", "02-byid-gt3-1.json", "02-byid-gt3-918.json")
            .map { it to daily(it) }

    @Test
    fun `singles-only ladder solves the easy boards and labels them Easy`() {
        easyBoards().forEach { (name, puzzle) ->
            val run = singlesOnly.run(puzzle)
            assertTrue("$name: $run", run.solved)
            assertEquals(name, SinglesRung, run.hardest)
            assertEquals(name, puzzle.size, run.steps)
            assertEquals(name, Difficulty.Level.Easy, Difficulty(run.hardest!!).level)
        }
    }

    @Test
    fun `the singles run places exactly the stored solution`() {
        easyBoards().forEach { (name, puzzle) ->
            var candidates = QueensCandidates.start(puzzle, emptySet())
            while (true) {
                val step = singlesOnly.next(puzzle, candidates) as? QueensDeduction.Place ?: break
                candidates = candidates.place(puzzle, step.cell)
            }
            assertEquals(name, puzzle.solution!!.map { it.idx }.toSet(), candidates.queens)
        }
    }

    @Test
    fun `every by-eye hint on a solution prefix carries the singles rung`() {
        val hints = QueensHints(ladder = singlesOnly)
        (easyBoards() + dailies()).forEach { (name, puzzle) ->
            val solution = puzzle.solution!!
            val reasons = (0 until solution.size).mapNotNull { k ->
                (hints.hint(puzzle, solution.take(k)) as QueensHint.Region).reason
            }
            assertTrue("$name gave no by-eye reason", reasons.isNotEmpty())
            reasons.forEach { assertEquals(name, SinglesRung, it.rung) }
        }
    }

    @Test
    fun `the lowest highlighted region wins over a lower unit index`() {
        // gt3-1 after its first queen: region 1's cell 13 (REGION proof) and column 4's
        // cell 60 are both singles; the hint order highlights the lowest region first.
        val puzzle = daily("02-byid-gt3-1.json")
        val placed = setOf(puzzle.solution!!.first().idx)
        val step = singlesOnly.next(puzzle, QueensCandidates.start(puzzle, placed))
        assertEquals(QueensDeduction.Place(HintReason(HintReason.Kind.REGION, 1), 13), step)
    }

    @Test
    fun `a row proof beats a column proof for the same cell`() {
        val puzzle = QueensTestSupport.n4Puzzle()
        val step = singlesOnly.next(puzzle, QueensCandidates.start(puzzle, setOf(7, 8, 14)))
        assertEquals(QueensDeduction.Place(HintReason(HintReason.Kind.ROW, 0), 1), step)
    }

    @Test
    fun `a board with no single stalls without a rung`() {
        val run = singlesOnly.run(QueensTestSupport.n4OpenPuzzle())
        assertFalse(run.solved)
        assertNull(run.hardest)
        assertEquals(0, run.steps)
    }

    @Test
    fun `the default ladder tries singles first`() {
        assertEquals(singlesOnly.run(QueensTestSupport.n4Puzzle()), QueensLadder.Default.run(QueensTestSupport.n4Puzzle()))
        assertEquals("singles", SinglesRung.id)
        assertEquals("queens.hint.rung.singles", SinglesRung.captionKey)
    }
}
