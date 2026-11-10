package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensTestSupport
import com.ivandsky.puzzlebox.games.queens.codec.QueensWireParser
import com.ivandsky.puzzlebox.games.queens.feed.QueensGameExtractor
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** `steps()` (ticket 07): the whole deduction list with each rung's own evidence, and `step` as its first element. */
class QueensRungStepsTest {

    private val propagation = listOf(SinglesRung, LineLockRung, PigeonholeRung)
    private val rungs: List<QueensRung> = propagation + LookAheadRung(propagation)

    private fun text(name: String): String =
        QueensRungStepsTest::class.java.getResourceAsStream("/fixtures/$name")!!.readBytes().decodeToString()

    private fun daily(name: String): QueensPuzzle {
        val body = text(name)
        val wire = QueensGameExtractor.extract(body)?.queensGamePuzzleJson ?: body
        return when (val r = QueensWireParser.parse(wire, LocalDate.of(2026, 11, 2))) {
            is DecodeResult.Ok -> r.value.puzzle
            is DecodeResult.Err -> throw AssertionError("$name parse failed: ${r.error}")
        }
    }

    private fun goldenBoards(): List<Pair<String, QueensPuzzle>> =
        Json.parseToJsonElement(text("queens-solver-golden.json")).jsonObject["boards"]!!.jsonArray.map { e ->
            val o = e.jsonObject
            fun ints(key: String) = o[key]!!.jsonArray.map { it.jsonPrimitive.content.toInt() }
            o["name"]!!.jsonPrimitive.content to QueensPuzzle(
                size = o["size"]!!.jsonPrimitive.content.toInt(),
                regions = ints("regions"),
                queensPerUnit = o["queensPerUnit"]!!.jsonPrimitive.content.toInt(),
                solution = ints("solve").map(::Cell),
            )
        }

    private val boards: List<Pair<String, QueensPuzzle>> = listOf(
        "queens-daily-ambiguous.json", "queens-daily-graphql.json", "queens-daily-real-916.json",
        "queens-daily-sample.json", "queens-daily-unique.json", "02-byid-gt3-1.json",
        "02-byid-gt3-916.json", "02-byid-gt3-918.json",
    ).map { it to daily(it) } + goldenBoards() + ("n8N2" to QueensTestSupport.n8N2Puzzle())

    /** Every state a Default-ladder solve passes through, from the empty board. */
    private fun states(puzzle: QueensPuzzle): List<QueensCandidates> {
        var state = QueensCandidates.start(puzzle, emptySet())
        val seen = mutableListOf(state)
        while (true) {
            state = when (val d = QueensLadder.Default.next(puzzle, state) ?: return seen) {
                is QueensDeduction.Place -> state.place(puzzle, d.cell)
                is QueensDeduction.Exclude -> state.exclude(d.cells)
            }
            seen += state
        }
    }

    private fun forEveryState(check: (String, QueensPuzzle, QueensCandidates) -> Unit) {
        for ((name, puzzle) in boards) states(puzzle).forEach { check(name, puzzle, it) }
    }

    private fun unitCells(puzzle: QueensPuzzle, unit: HintReason): Set<Int> {
        val n = puzzle.size
        return when (unit.kind) {
            HintReason.Kind.ROW -> (0 until n).map { unit.unit * n + it }
            HintReason.Kind.COLUMN -> (0 until n).map { it * n + unit.unit }
            HintReason.Kind.REGION -> puzzle.regions.indices.filter { puzzle.regions[it] == unit.unit }
        }.toSet()
    }

    @Test
    fun `look ahead step is the first of its lazy steps`() = forEveryState { name, puzzle, state ->
        val rung = rungs.last()
        assertEquals(name, rung.steps(puzzle, state).firstOrNull()?.deduction, rung.step(puzzle, state))
    }

    @Test
    fun `every deduction is applicable and its evidence is non-empty and clear of what it excludes`() = forEveryState { name, puzzle, state ->
        for (rung in rungs) for (proof in rung.steps(puzzle, state)) {
            val where = "$name ${rung.id} ${proof.deduction}"
            assertTrue(where, proof.evidence.isNotEmpty())
            when (val d = proof.deduction) {
                is QueensDeduction.Place -> assertTrue(where, state.isOpen(d.cell))
                is QueensDeduction.Exclude -> {
                    assertTrue(where, d.cells.isNotEmpty() && d.cells.all(state::isOpen))
                    assertTrue(where, d.cells.intersect(proof.evidence).isEmpty())
                }
            }
        }
    }

    @Test
    fun `singles evidence is the cells of the unit that has one open cell left`() = forEveryState { name, puzzle, state ->
        for (proof in SinglesRung.steps(puzzle, state)) {
            val d = proof.deduction as QueensDeduction.Place
            assertEquals(name, unitCells(puzzle, d.unit), proof.evidence)
        }
    }

    @Test
    fun `line lock evidence is the open cells of the exact locking unit`() = forEveryState { name, puzzle, state ->
        for (proof in LineLockRung.steps(puzzle, state)) {
            val d = proof.deduction as QueensDeduction.Exclude
            fun open(cells: Set<Int>) = cells.filter(state::isOpen).toSet()
            val lockingLine = open(unitCells(puzzle, d.unit))
            val lockingRegions = puzzle.regions.indices.map { r -> open(unitCells(puzzle, HintReason(HintReason.Kind.REGION, r))) }
            assertTrue("$name ${proof.evidence} for ${d.unit}", proof.evidence == lockingLine || proof.evidence in lockingRegions)
        }
    }

    @Test
    fun `pigeonhole evidence is the open cells of at least two whole units`() = forEveryState { name, puzzle, state ->
        for (proof in PigeonholeRung.steps(puzzle, state)) {
            val n = puzzle.size
            val perKind = listOf(
                HintReason.Kind.ROW to proof.evidence.map { it / n },
                HintReason.Kind.COLUMN to proof.evidence.map { it % n },
                HintReason.Kind.REGION to proof.evidence.map { puzzle.regions[it] },
            ).map { (kind, units) -> units.toSet().map { HintReason(kind, it) } }
            val named = perKind.any { units ->
                units.size >= 2 &&
                    units.flatMap { unitCells(puzzle, it) }.filter(state::isOpen).toSet() == proof.evidence
            }
            assertTrue("$name ${proof.evidence}", named)
        }
    }

    @Test
    fun `look ahead evidence is the open cells of the emptied unit, without the probed cell`() = forEveryState { name, puzzle, state ->
        for (proof in rungs.last().steps(puzzle, state)) {
            val d = proof.deduction as QueensDeduction.Exclude
            assertEquals(name, unitCells(puzzle, d.unit).filter { state.isOpen(it) && it !in d.cells }.toSet(), proof.evidence)
        }
    }

    @Test
    fun `steps is deterministic`() = forEveryState { name, puzzle, state ->
        for (rung in rungs) assertEquals("$name ${rung.id}", rung.steps(puzzle, state).toList(), rung.steps(puzzle, state).toList())
    }

    /** Region 3 locks column 4 (excluding 4, 9, 14) and region 4 locks row 4 (excluding 24). */
    private fun twoLocks(): QueensPuzzle = QueensPuzzle(
        size = 5,
        regions = listOf(
            0, 0, 1, 1, 1,
            0, 0, 1, 1, 1,
            2, 0, 1, 1, 1,
            2, 2, 1, 1, 3,
            4, 4, 4, 4, 3,
        ),
        solution = QueensTestSupport.path(1, 8, 10, 19, 22),
    )

    @Test
    fun `steps lists every lock the board has, in step order`() {
        val puzzle = twoLocks()
        val state = QueensCandidates.start(puzzle, emptySet())
        val proofs = LineLockRung.steps(puzzle, state).toList()
        assertEquals(2, proofs.size)
        assertEquals(QueensDeduction.Exclude(HintReason(HintReason.Kind.COLUMN, 4, LineLockRung), setOf(4, 9, 14)), proofs.first().deduction)
        assertEquals(setOf(19, 24), proofs.first().evidence)
        assertEquals(
            QueensDeduction.Exclude(HintReason(HintReason.Kind.ROW, 4, LineLockRung), setOf(24)),
            proofs[1].deduction,
        )
        assertEquals(setOf(20, 21, 22, 23), proofs[1].evidence)
    }

    private class CountingRung(private val inner: QueensRung) : QueensRung by inner {
        var calls = 0
        override fun step(puzzle: QueensPuzzle, candidates: QueensCandidates): QueensDeduction? {
            calls++
            return inner.step(puzzle, candidates)
        }
    }

    @Test
    fun `look ahead steps is lazy and step stops at the first failed probe`() {
        val (puzzle, state) = boards.firstNotNullOf { (_, p) ->
            states(p).firstOrNull { LookAheadRung(propagation).steps(p, it).drop(1).firstOrNull() != null }?.let { p to it }
        }
        val counter = CountingRung(SinglesRung)
        val rung = LookAheadRung(listOf(counter))
        rung.steps(puzzle, state).first()
        val forFirst = counter.calls
        counter.calls = 0
        rung.steps(puzzle, state).toList()
        val forAll = counter.calls
        assertTrue("first=$forFirst all=$forAll", forFirst < forAll)
        counter.calls = 0
        rung.step(puzzle, state)
        assertEquals(forFirst, counter.calls)
    }
}
