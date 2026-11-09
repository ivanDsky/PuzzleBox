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

    private fun text(name: String): String = javaClass.getResourceAsStream("/fixtures/$name")!!.readBytes().decodeToString()

    private fun daily(name: String): QueensPuzzle {
        val body = text(name)
        val wire = QueensGameExtractor.extract(body)?.queensGamePuzzleJson ?: body
        return when (val r = QueensWireParser.parse(wire, LocalDate.of(2026, 11, 2))) {
            is DecodeResult.Ok -> r.value.puzzle
            is DecodeResult.Err -> throw AssertionError("$name parse failed: ${r.error}")
        }
    }

    private fun JsonObject.ints(key: String): List<Int> = getValue(key).jsonArray.map { it.jsonPrimitive.content.toInt() }

    private fun goldenBoards(): List<Pair<String, QueensPuzzle>> =
        Json.parseToJsonElement(text("queens-solver-golden.json")).jsonObject.getValue("boards").jsonArray.map { e ->
            val o = e.jsonObject
            o.getValue("name").jsonPrimitive.content to QueensPuzzle(
                size = o.getValue("size").jsonPrimitive.content.toInt(),
                regions = o.ints("regions"),
                queensPerUnit = o.getValue("queensPerUnit").jsonPrimitive.content.toInt(),
                solution = o.ints("solve").map(::Cell),
            )
        }

    /** An editor or difficulty fixture: size, regions and queens per unit (1 when absent). */
    private fun board(name: String): QueensPuzzle {
        val o = Json.parseToJsonElement(text(name)).jsonObject
        return QueensPuzzle(
            size = o.getValue("size").jsonPrimitive.content.toInt(),
            regions = o.ints("regions"),
            queensPerUnit = o["queensPerUnit"]?.jsonPrimitive?.content?.toInt() ?: 1,
        )
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
    fun `stepwise outcome on every other fixture board is pinned`() {
        val boards = goldenBoards() + listOf(
            "queens-daily-ambiguous.json" to daily("queens-daily-ambiguous.json"),
            "queens-daily-sample.json" to daily("queens-daily-sample.json"),
            "n8N2" to QueensTestSupport.n8N2Puzzle(),
        ) + listOf(
            "queens-difficulty-easy.json", "queens-difficulty-medium.json", "queens-difficulty-hard.json",
            "queens-difficulty-expert.json", "queens-difficulty-guessing.json",
            "queens-editor-two-solutions.json", "queens-editor-clash.json", "queens-editor-worst-12.json",
        ).map { it to board(it) }
        val outcomes = boards.associate { (name, puzzle) ->
            val run = QueensLadder.Stepwise.run(puzzle)
            name to (run.solved to run.hardest?.id)
        }
        // solved, hardest rung used
        val expected = mapOf(
            "gen-n8-q1" to (true to "long-look-ahead"),
            "gen-n9-q1" to (true to "long-look-ahead"),
            "gen-n10-q1" to (true to "short-look-ahead"),
            "gen-n8-q2" to (true to "long-look-ahead"),
            "queens-daily-ambiguous.json" to (false to "long-look-ahead"),
            "queens-daily-sample.json" to (false to "pigeonhole"),
            "n8N2" to (true to "long-look-ahead"),
            "queens-difficulty-easy.json" to (true to "singles"),
            "queens-difficulty-medium.json" to (true to "line-lock"),
            "queens-difficulty-hard.json" to (true to "shared-neighbours"),
            "queens-difficulty-expert.json" to (true to "shared-neighbours"),
            "queens-difficulty-guessing.json" to (true to "long-look-ahead"),
            "queens-editor-two-solutions.json" to (false to "line-lock"),
            "queens-editor-clash.json" to (false to "line-lock"),
            "queens-editor-worst-12.json" to (false to "short-look-ahead"),
        )
        assertEquals(expected, outcomes)
    }

    /** Every state a Stepwise solve passes through from the empty board, then the solution stood queen by queen with nothing crossed. */
    private fun states(puzzle: QueensPuzzle): List<QueensCandidates> {
        var state = QueensCandidates.start(puzzle, emptySet())
        val seen = mutableListOf(state)
        while (true) {
            state = when (val d = QueensLadder.Stepwise.next(puzzle, state) ?: break) {
                is QueensDeduction.Place -> state.place(puzzle, d.cell)
                is QueensDeduction.Exclude -> state.exclude(d.cells)
            }
            seen += state
        }
        val solution = puzzle.solution!!.map { it.idx }.sorted()
        return seen + (1 until solution.size).map { QueensCandidates.start(puzzle, solution.take(it).toSet()) }
    }

    @Test
    fun `no cell a look-ahead rung crosses on a golden board is a solution cell`() {
        val lookAheads = QueensLadder.Stepwise.rungs.takeLast(2)
        var fired = 0
        for ((name, puzzle) in goldenBoards()) {
            val solution = puzzle.solution!!.map { it.idx }.toSet()
            for (state in states(puzzle)) for (rung in lookAheads) for (proof in rung.steps(puzzle, state)) {
                fired++
                val d = proof.deduction as QueensDeduction.Exclude
                assertTrue("$name ${rung.id} $d", d.cells.none { it in solution })
                assertTrue("$name ${rung.id} $d", d.cells.all(state::isOpen) && d.cells.intersect(proof.evidence).isEmpty())
            }
        }
        assertTrue(fired > 0)
    }
}
