package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensTestSupport
import com.ivandsky.puzzlebox.games.queens.codec.QueensWireParser
import com.ivandsky.puzzlebox.games.queens.feed.QueensGameExtractor
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** `QueensLadder.Stepwise`: the catalog's D0–D11 in order, solving the dailies, with `Default` left as it was. */
class StepwiseLadderTest {

    /** One Stepwise solve from the empty board: every state it passed through and the deductions between them. */
    private class Solve(val puzzle: QueensPuzzle, val states: List<QueensCandidates>, val used: List<QueensRung>) {
        val solved: Boolean get() = states.last().queens.size == puzzle.size * puzzle.queensPerUnit
        val hardest: String? get() = used.maxByOrNull { QueensLadder.Stepwise.rungs.indexOf(it) }?.id
    }

    private companion object {
        fun text(name: String): String = StepwiseLadderTest::class.java.getResourceAsStream("/fixtures/$name")!!.readBytes().decodeToString()

        fun daily(name: String): QueensPuzzle {
            val body = text(name)
            val wire = QueensGameExtractor.extract(body)?.queensGamePuzzleJson ?: body
            return when (val r = QueensWireParser.parse(wire, LocalDate.of(2026, 11, 2))) {
                is DecodeResult.Ok -> r.value.puzzle
                is DecodeResult.Err -> throw AssertionError("$name parse failed: ${r.error}")
            }
        }

        fun JsonObject.ints(key: String): List<Int>? = get(key)?.jsonArray?.map { it.jsonPrimitive.content.toInt() }

        fun golden(name: String): QueensPuzzle {
            val boards = Json.parseToJsonElement(text("queens-solver-golden.json")).jsonObject.getValue("boards").jsonArray
            val o = boards.map { it.jsonObject }.single { it.getValue("name").jsonPrimitive.content == name }
            return QueensPuzzle(
                size = o.getValue("size").jsonPrimitive.content.toInt(),
                regions = o.ints("regions")!!,
                queensPerUnit = o.getValue("queensPerUnit").jsonPrimitive.content.toInt(),
                solution = o.ints("solve")!!.map(::Cell),
            )
        }

        /** An editor or difficulty fixture, with its (first) solution when it lists one. */
        fun board(name: String): QueensPuzzle {
            val o = Json.parseToJsonElement(text(name)).jsonObject
            return QueensPuzzle(
                size = o.getValue("size").jsonPrimitive.content.toInt(),
                regions = o.ints("regions")!!,
                queensPerUnit = o["queensPerUnit"]?.jsonPrimitive?.content?.toInt() ?: 1,
                solution = (o.ints("solution") ?: o.ints("solutionA"))?.map(::Cell),
            )
        }

        fun solve(puzzle: QueensPuzzle): Solve {
            var state = QueensCandidates.start(puzzle, emptySet())
            val states = mutableListOf(state)
            val used = mutableListOf<QueensRung>()
            while (true) {
                val deduction = QueensLadder.Stepwise.next(puzzle, state) ?: break
                state = when (deduction) {
                    is QueensDeduction.Place -> state.place(puzzle, deduction.cell)
                    is QueensDeduction.Exclude -> state.exclude(deduction.cells)
                }
                states += state
                used += deduction.rung
            }
            return Solve(puzzle, states, used)
        }

        /** Every board this class solves, each solved once for all tests. */
        val solves: Map<String, Solve> by lazy {
            listOf(
                "gen-n8-q1" to golden("gen-n8-q1"),
                "gen-n8-q2" to golden("gen-n8-q2"),
                "n8N2" to QueensTestSupport.n8N2Puzzle(),
                "queens-daily-ambiguous.json" to daily("queens-daily-ambiguous.json"),
                "queens-daily-sample.json" to daily("queens-daily-sample.json"),
            ).plus(
                listOf(
                    "queens-difficulty-easy.json", "queens-difficulty-medium.json", "queens-difficulty-hard.json",
                    "queens-difficulty-expert.json", "queens-difficulty-guessing.json",
                    "queens-editor-two-solutions.json", "queens-editor-clash.json",
                ).map { it to board(it) },
            ).associate { (name, puzzle) -> name to solve(puzzle) }
        }
    }

    /**
     * The daily fixtures with one solution. `queens-daily-ambiguous.json` has two and the
     * hand-drawn `queens-daily-sample.json` has 408, so no sound ladder finishes either.
     */
    private val uniqueDailies = listOf(
        "queens-daily-graphql.json", "queens-daily-real-916.json", "queens-daily-unique.json",
        "02-byid-gt3-1.json", "02-byid-gt3-916.json", "02-byid-gt3-918.json",
    )

    @Test
    fun `stepwise lists the catalog rungs D0 to D11 in order`() {
        val ids = QueensLadder.Stepwise.rungs.map { it.id }
        assertEquals(
            listOf(
                "attack", "singles", "line-lock", "shared-neighbours", "pigeonhole", "line-group-pigeonhole",
                "leftover-queen", "squeeze", "count-squeeze", "short-look-ahead", "long-look-ahead",
            ),
            ids,
        )
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `default is still the four legacy rungs`() {
        assertEquals(listOf("singles", "line-lock", "pigeonhole", "look-ahead"), QueensLadder.Default.rungs.map { it.id })
    }

    @Test
    fun `stepwise solves every unique daily fixture`() {
        for (name in uniqueDailies) assertTrue(name, QueensLadder.Stepwise.run(daily(name)).solved)
    }

    @Test
    fun `stepwise outcome on the other fixture boards is pinned`() {
        // Solved boards pin the hardest rung used; stalled ones only that they stall.
        val expected = mapOf(
            "gen-n8-q1" to "long-look-ahead",
            "gen-n8-q2" to "long-look-ahead",
            "n8N2" to "long-look-ahead",
            "queens-daily-ambiguous.json" to null,
            "queens-daily-sample.json" to null,
            "queens-difficulty-easy.json" to "singles",
            "queens-difficulty-medium.json" to "line-lock",
            "queens-difficulty-hard.json" to "shared-neighbours",
            "queens-difficulty-expert.json" to "shared-neighbours",
            "queens-difficulty-guessing.json" to "long-look-ahead",
            "queens-editor-two-solutions.json" to null,
            "queens-editor-clash.json" to null,
        )
        assertEquals(expected, solves.mapValues { (_, s) -> s.hardest.takeIf { s.solved } })
    }

    @Test
    fun `no cell a look-ahead rung crosses is a solution cell`() {
        val lookAheads = QueensLadder.Stepwise.rungs.takeLast(2)
        var fired = 0
        for (name in listOf("gen-n8-q1", "gen-n8-q2", "n8N2", "queens-editor-two-solutions.json")) {
            val s = solves.getValue(name)
            val solution = s.puzzle.solution!!.map { it.idx }.sorted()
            // The states the solve took a look-ahead step from, then the solution stood every other queen with nothing crossed.
            val reached = s.states.filterIndexed { i, _ -> s.used.getOrNull(i) in lookAheads }
            val prefixes = (1 until solution.size step 2).map { QueensCandidates.start(s.puzzle, solution.take(it).toSet()) }
            for (state in reached + prefixes) for (rung in lookAheads) for (proof in rung.steps(s.puzzle, state)) {
                fired++
                val d = proof.deduction as QueensDeduction.Exclude
                assertTrue("$name ${rung.id} $d", d.cells.none { it in solution })
                assertTrue("$name ${rung.id} $d", d.cells.all(state::isOpen) && d.cells.intersect(proof.evidence).isEmpty())
            }
        }
        assertTrue(fired > 0)
    }
}
