package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.games.queens.engine.solve.QueensSolver
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.random.Random

/**
 * Golden solver snapshot (zip precedent: ZipSolverGoldenSnapshotTest): the recorded
 * `solve()` + `countSolutions()` outputs over deterministically generated boards at the
 * official sizes (N=1, n8/n9/n10) plus the N=2 synthetic shape — captured PRE-future
 * refactors. Boards travel by their full data (size, queensPerUnit, regions, solution),
 * never by regeneration inside the assertion path... except that the fixture generator IS
 * seed-deterministic, so the boards themselves are rebuilt from the pinned seed list and
 * checked against the recorded numbers (regions+solution bytes are asserted equal too).
 * Re-record deliberately with `QUEENS_GOLDEN_REGEN=1` (writes
 * `src/test/resources/fixtures/queens-solver-golden.json`).
 */
class QueensSolverGoldenSnapshotTest {

    private data class GoldenBoard(
        val name: String,
        val size: Int,
        val queensPerUnit: Int,
        val seed: Long,
        val regions: List<Int>,
        val solve: List<Int>,
        val countSolutions: Int,
    )

    private val resourcePath = "/fixtures/queens-solver-golden.json"
    private val regenPath = "src/test/resources/fixtures/queens-solver-golden.json"

    /** Pinned seed list: every seed yields a unique board via [QueensGenerator.nextUnique]. */
    private val workloads = listOf(
        Triple("gen-n8-q1", 8, 1) to 592L,
        Triple("gen-n9-q1", 9, 1) to 2024L,
        Triple("gen-n10-q1", 10, 1) to 777L,
        Triple("gen-n8-q2", 8, 2) to 42L,
    )

    private fun generate(): List<GoldenBoard> = workloads.map { (spec, seed) ->
        val (name, size, q) = spec
        val puzzle = QueensGenerator(Random(seed)).nextUnique(size, q)
            ?: throw AssertionError("no unique board for $name (seed $seed)")
        val count = QueensSolver().countSolutions(puzzle)
        assertEquals(1, count)
        GoldenBoard(
            name = name,
            size = size,
            queensPerUnit = q,
            seed = seed,
            regions = puzzle.regions,
            solve = puzzle.solution!!.map { it.idx },
            countSolutions = count,
        )
    }

    private fun parse(text: String): List<GoldenBoard> =
        Json.parseToJsonElement(text).jsonObject["boards"]!!.jsonArray.map { element ->
            val o = element.jsonObject
            GoldenBoard(
                name = o["name"]!!.jsonPrimitive.content,
                size = o["size"]!!.jsonPrimitive.content.toInt(),
                queensPerUnit = o["queensPerUnit"]!!.jsonPrimitive.content.toInt(),
                seed = o["seed"]!!.jsonPrimitive.content.toLong(),
                regions = o["regions"]!!.jsonArray.map { it.jsonPrimitive.content.toInt() },
                solve = o["solve"]!!.jsonArray.map { it.jsonPrimitive.content.toInt() },
                countSolutions = o["countSolutions"]!!.jsonPrimitive.content.toInt(),
            )
        }

    private fun serialize(boards: List<GoldenBoard>): String = buildString {
        appendLine("{")
        appendLine("""  "description": "Solver outputs over seed-deterministic generated boards; re-record only with QUEENS_GOLDEN_REGEN=1 (env var)",""")
        appendLine("""  "boards": [""")
        boards.forEachIndexed { i, b ->
            val regions = b.regions.joinToString(",", "[", "]")
            val solve = b.solve.joinToString(",", "[", "]")
            val comma = if (i == boards.lastIndex) "" else ","
            appendLine(
                """    {"name":"${b.name}","size":${b.size},"queensPerUnit":${b.queensPerUnit},""" +
                    """"seed":${b.seed},"regions":$regions,"solve":$solve,"countSolutions":${b.countSolutions}}$comma""",
            )
        }
        appendLine("  ]")
        append("}")
    }

    @Test
    fun `golden snapshot - solve and countSolutions are bit-stable`() {
        val recorded = if (System.getenv("QUEENS_GOLDEN_REGEN") == "1") {
            val boards = generate()
            java.io.File(regenPath).writeText(serialize(boards))
            boards
        } else {
            parse(
                QueensSolverGoldenSnapshotTest::class.java
                    .getResourceAsStream(resourcePath)!!.readBytes().decodeToString(),
            )
        }
        assertEquals(workloads.map { it.first.first }, recorded.map { it.name })

        recorded.forEach { golden ->
            val puzzle = QueensGenerator(Random(golden.seed)).nextUnique(golden.size, golden.queensPerUnit)
            assertNotNull("seed ${golden.seed} stopped producing a board", puzzle)
            // The regenerated board IS the recorded board (regions byte-identical).
            assertEquals("region drift on ${golden.name}", golden.regions, puzzle!!.regions)
            val solver = QueensSolver()
            assertEquals("count drift on ${golden.name}", golden.countSolutions, solver.countSolutions(puzzle))
            assertEquals("solve drift on ${golden.name}", golden.solve, solver.solve(puzzle)!!.map { it.idx })
        }
    }
}
