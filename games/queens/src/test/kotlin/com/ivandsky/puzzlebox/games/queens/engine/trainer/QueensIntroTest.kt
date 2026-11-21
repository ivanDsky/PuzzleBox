package com.ivandsky.puzzlebox.games.queens.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensBoard
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensTestSupport
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensLadder
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The step an intro demo is cut from: the one that opens a Focus board, on the three dailies and every 25th corpus board. */
class QueensIntroTest {

    private val lineLock = TechniqueId("line-lock")

    @Test
    fun `the opening is the focus board's start and the step practice makes on it`() {
        var openings = 0
        for ((name, puzzle) in boards) {
            for (technique in QueensLadder.Stepwise.rungs.mapNotNull { it.trainerId }) {
                val start = QueensPractice.start(puzzle, technique, BoardKind.Focus)
                val opening = QueensIntro.opening(puzzle, technique)
                if (start == null) {
                    assertNull("$name ${technique.value}", opening)
                    continue
                }
                openings++
                opening!!
                assertEquals("$name ${technique.value}", start, opening.board)
                assertEquals("$name ${technique.value}", QueensPractice.show(puzzle, start), opening.step)
                assertEquals(technique, opening.step.unit.rung.trainerId)
                assertTrue(opening.landing.isNotEmpty())
                val made = when (val result = opening.step.result) {
                    is QueensStep.Deduction.Result.Cross -> {
                        assertEquals(QueensPractice.crossings(puzzle, start, opening.step), opening.landing)
                        start.copy(manual = start.manual + opening.landing)
                    }
                    is QueensStep.Deduction.Result.LastCell -> {
                        assertEquals(listOf(result.cell), opening.landing)
                        start.copy(queens = start.queens + result.cell)
                    }
                }
                assertEquals("$name ${technique.value}", made, opening.after)
            }
        }
        assertTrue(openings > 0)
    }

    @Test
    fun `a line lock step fills the line when its crossed squares share the named line, the region otherwise`() {
        var fillsLine = 0
        var fillsRegion = 0
        for ((name, puzzle) in boards) {
            val step = QueensIntro.opening(puzzle, lineLock)?.step ?: continue
            val crossed = (step.result as QueensStep.Deduction.Result.Cross).cells
            val evidence = step.evidence
            fun region(cell: Cell) = puzzle.regions[cell.idx]
            fun inOneLine(cells: Collection<Cell>) =
                cells.map { it.idx / puzzle.size }.distinct().size == 1 || cells.map { it.idx % puzzle.size }.distinct().size == 1
            if (QueensIntro.fillsLine(puzzle, step)) {
                fillsLine++
                // A color's open squares sit in one line, and the rest of that line is crossed.
                assertEquals(name, 1, evidence.map(::region).distinct().size)
                assertTrue(name, crossed.none { region(it) == region(evidence.first()) })
                assertTrue(name, inOneLine(evidence + crossed))
            } else {
                fillsRegion++
                // A line's open squares sit in one color, and the rest of that color, off the line, is crossed.
                assertTrue(name, inOneLine(evidence))
                assertEquals(name, 1, (evidence + crossed).map(::region).distinct().size)
                assertTrue(name, !inOneLine(evidence + crossed))
            }
        }
        assertTrue("a Fill the line opening in the sample", fillsLine > 0)
        assertTrue("a Fill the region opening in the sample", fillsRegion > 0)
    }

    @Test
    fun `no opening where no focus board exists`() {
        assertNull(QueensIntro.opening(QueensTestSupport.daily("queens-daily-sample.json"), TechniqueId("singles")))
        assertNull(QueensIntro.opening(QueensTestSupport.daily("queens-daily-real-916.json"), TechniqueId("not-a-technique")))
        assertNotNull(QueensIntro.opening(QueensTestSupport.daily("queens-daily-real-916.json"), lineLock))
        assertEquals(QueensBoard(queens = listOf(Cell(6))), QueensIntro.opening(QueensTestSupport.daily("queens-daily-real-916.json"), lineLock)!!.board)
    }

    private companion object {
        val boards: Map<String, QueensPuzzle> by lazy {
            val dailies = mapOf(
                "real-916" to QueensTestSupport.daily("queens-daily-real-916.json"),
                "unique" to QueensTestSupport.daily("queens-daily-unique.json"),
                "sample" to QueensTestSupport.daily("queens-daily-sample.json"),
            )
            val text = QueensIntroTest::class.java.getResourceAsStream("/fixtures/queens-hint-corpus.json")!!.readBytes().decodeToString()
            val corpus = Json.parseToJsonElement(text).jsonArray.filterIndexed { i, _ -> i % 25 == 0 }.associate { record ->
                val board = record.jsonObject
                board.getValue("id").jsonPrimitive.content to QueensPuzzle(
                    size = board.getValue("size").jsonPrimitive.content.toInt(),
                    regions = board.getValue("regions").jsonArray.map { it.jsonPrimitive.content.toInt() },
                    solution = board.getValue("solution").jsonArray.map { Cell(it.jsonPrimitive.content.toInt()) },
                )
            }
            dailies + corpus
        }
    }
}
