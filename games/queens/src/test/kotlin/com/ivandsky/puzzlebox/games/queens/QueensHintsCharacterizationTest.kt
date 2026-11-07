package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.queens.codec.QueensWireParser
import com.ivandsky.puzzlebox.games.queens.engine.hints.QueensHint
import com.ivandsky.puzzlebox.games.queens.engine.hints.QueensHints
import com.ivandsky.puzzlebox.games.queens.feed.QueensGameExtractor
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P14 characterization (spec A9): today's [QueensHints.hint] answer — cell, reason kind and
 * unit — pinned over every committed daily fixture plus the solver-golden boards, at the
 * empty board and every prefix of the stored solution (in stored order). The hint ladder
 * must keep every recorded line. Re-record deliberately with `QUEENS_HINTS_GOLDEN_REGEN=1`
 * (writes `src/test/resources/fixtures/queens-hints-golden.txt`).
 */
class QueensHintsCharacterizationTest {

    private val resourcePath = "/fixtures/queens-hints-golden.txt"
    private val regenPath = "src/test/resources/fixtures/queens-hints-golden.txt"
    private val date = LocalDate.of(2026, 11, 2)

    private val dailyFixtures = listOf(
        "queens-daily-ambiguous.json",
        "queens-daily-graphql.json",
        "queens-daily-real-916.json",
        "queens-daily-sample.json",
        "queens-daily-unique.json",
        "02-byid-gt3-1.json",
        "02-byid-gt3-916.json",
        "02-byid-gt3-918.json",
    )

    private fun text(name: String): String =
        QueensHintsCharacterizationTest::class.java.getResourceAsStream("/fixtures/$name")!!
            .readBytes().decodeToString()

    /** Stream/by-id bodies go through the extractor; bare wire shapes parse directly. */
    private fun daily(name: String): QueensPuzzle {
        val body = text(name)
        val wire = QueensGameExtractor.extract(body)?.queensGamePuzzleJson ?: body
        return when (val r = QueensWireParser.parse(wire, date)) {
            is DecodeResult.Ok -> r.value.puzzle
            is DecodeResult.Err -> throw AssertionError("$name parse failed: ${r.error}")
        }
    }

    private fun goldenBoards(): List<Pair<String, QueensPuzzle>> =
        Json.parseToJsonElement(text("queens-solver-golden.json")).jsonObject["boards"]!!.jsonArray.map { e ->
            val o = e.jsonObject
            fun ints(key: String) = o[key]!!.jsonArray.map { it.jsonPrimitive.content.toInt() }
            "queens-solver-golden.json#" + o["name"]!!.jsonPrimitive.content to QueensPuzzle(
                size = o["size"]!!.jsonPrimitive.content.toInt(),
                regions = ints("regions"),
                queensPerUnit = o["queensPerUnit"]!!.jsonPrimitive.content.toInt(),
                solution = ints("solve").map(::Cell),
            )
        }

    private fun boards(): List<Pair<String, QueensPuzzle>> =
        dailyFixtures.map { it to daily(it) } + goldenBoards()

    private fun render(hint: QueensHint): String = when (hint) {
        is QueensHint.Region ->
            "Region region=${hint.region} cell=${hint.cell.idx} reason=" +
                (hint.reason?.let { "${it.kind}:${it.unit}" } ?: "none")
        is QueensHint.Incorrect -> "Incorrect cells=" + hint.cells.joinToString(",") { it.idx.toString() }
        QueensHint.None -> "None"
    }

    /** One line per sampled stage: `board|placedCount|hint`. */
    private fun observe(): List<String> {
        val hints = QueensHints()
        return boards().flatMap { (name, puzzle) ->
            val solution = puzzle.solution ?: throw AssertionError("$name ships no solution")
            (0..solution.size).map { k -> "$name|$k|${render(hints.hint(puzzle, solution.take(k)))}" }
        }
    }

    @Test
    fun `today's hint answers are pinned for every sampled stage of every fixture`() {
        val observed = observe()
        val recorded = if (System.getenv("QUEENS_HINTS_GOLDEN_REGEN") == "1") {
            java.io.File(regenPath).writeText(observed.joinToString("\n", postfix = "\n"))
            observed
        } else {
            text("queens-hints-golden.txt").lines().filter { it.isNotBlank() }
        }
        assertEquals(resourcePath, recorded.joinToString("\n"), observed.joinToString("\n"))
    }
}
