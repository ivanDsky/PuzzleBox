package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.zip.codec.ZipCodec
import com.ivandsky.puzzlebox.games.zip.codec.ZipWireParser
import com.ivandsky.puzzlebox.games.zip.engine.ZipRules
import com.ivandsky.puzzlebox.games.zip.engine.ZipSolver
import java.time.LocalDate
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden solver snapshot (A18): the recorded outputs of `solve()` + `countSolutions()`
 * over the #592 fixture and 20 deterministically generated n7 boards, captured PRE-refactor.
 * The refactor (rules pipeline, solver split, hint switch) must leave every recorded number
 * bit-identical — boards travel by ZipCodec bytes, never by regeneration, so the fixture
 * cannot drift with the solver. Re-record deliberately with `ZIP_GOLDEN_REGEN=1`
 * (writes `src/test/resources/fixtures/zip-solver-golden.json`).
 */
class ZipSolverGoldenSnapshotTest {

    private data class GoldenBoard(val name: String, val codecB64: String, val solve: List<Int>?, val countSolutions: Int)

    private val resourcePath = "/fixtures/zip-solver-golden.json"
    private val regenPath = "src/test/resources/fixtures/zip-solver-golden.json"

    private fun parse(text: String): List<GoldenBoard> =
        Json.parseToJsonElement(text).jsonObject["boards"]!!.jsonArray.map { element ->
            val o = element.jsonObject
            GoldenBoard(
                name = o["name"]!!.jsonPrimitive.content,
                codecB64 = o["codec"]!!.jsonPrimitive.content,
                solve = o["solve"]?.jsonArray?.map { it.jsonPrimitive.content.toInt() },
                countSolutions = o["countSolutions"]!!.jsonPrimitive.content.toInt(),
            )
        }

    private fun serialize(boards: List<GoldenBoard>): String = buildString {
        appendLine("{")
        appendLine("""  "description": "Pre-A18-refactor solver outputs; re-record only with ZIP_GOLDEN_REGEN=1 (env var)",""")
        appendLine("""  "boards": [""")
        boards.forEachIndexed { i, b ->
            val solve = b.solve?.joinToString(",", "[", "]") ?: "null"
            val comma = if (i == boards.lastIndex) "" else ","
            appendLine("""    {"name":"${b.name}","codec":"${b.codecB64}","solve":$solve,"countSolutions":${b.countSolutions}}$comma""")
        }
        appendLine("  ]")
        append("}")
    }

    /**
     * Fixture #592 first, then 20 generated n7 boards from a FIXED seed list: each seed's
     * FIRST `next(wallProbability = 0.85)` attempt is uniquely solvable (measured once,
     * 2026-10-31, pre-refactor — ZipGenerator is seed-deterministic). Single attempts, so
     * re-recording never wanders into the pathological sparse-board outliers.
     */
    private val n7WinnerSeeds = listOf(
        2, 4, 5, 8, 11, 12, 14, 15, 18, 19, 21, 22, 24, 25, 27, 29, 30, 32, 34, 36,
    )

    private fun capture(): List<GoldenBoard> {
        val solver = ZipSolver()
        val boards = mutableListOf<Pair<String, com.ivandsky.puzzlebox.games.zip.ZipPuzzle>>()

        val fixtureJson = ZipSolverGoldenSnapshotTest::class.java
            .getResourceAsStream("/fixtures/zip-daily-592.json")!!.readBytes().decodeToString()
        val daily = (ZipWireParser.parse(fixtureJson, LocalDate.of(2026, 10, 30), "592") as DecodeResult.Ok).value
        boards += "daily-592" to daily.puzzle

        val n7 = com.ivandsky.puzzlebox.games.zip.engine.ZipVariants.byId(
            com.ivandsky.puzzlebox.game.VariantId("zip/n7"),
        )!!
        for (seed in n7WinnerSeeds) {
            val puzzle = com.ivandsky.puzzlebox.games.zip.engine.ZipGenerator(kotlin.random.Random(seed))
                .next(n7, wallProbability = 0.85)
            check(puzzle != null) { "winner seed $seed regressed: not uniquely solvable" }
            check(solver.countSolutions(puzzle) == 1) { "winner seed $seed regressed: not unique" }
            boards += "gen-n7-seed-%04d".format(seed) to puzzle
        }
        check(boards.size == 21) { "golden needs the fixture + 20 generated boards, got ${boards.size}" }

        return boards.map { (name, puzzle) ->
            GoldenBoard(
                name = name,
                codecB64 = Base64.getEncoder().encodeToString(ZipCodec.encode(puzzle)),
                solve = solver.solve(puzzle)?.map { it.idx },
                countSolutions = solver.countSolutions(puzzle),
            )
        }
    }

    /** Regenerate the golden file (deliberate maintenance, run pre-refactor). */
    @Test
    fun `regen - re-record the golden snapshot`() {
        if (System.getenv("ZIP_GOLDEN_REGEN") == null) return
        var dir: java.io.File? = java.io.File(System.getProperty("user.dir"))
        repeat(6) {
            if (dir == null) return@repeat
            val candidate = dir!!.resolve(regenPath)
            if (candidate.parentFile.isDirectory) {
                candidate.writeText(serialize(capture()))
                println("GOLDEN wrote ${candidate.absolutePath}")
                return
            }
            dir = dir!!.parentFile
        }
        error("fixtures source dir not found from ${System.getProperty("user.dir")}")
    }

    /** The binding gate: live solver output == recorded output, byte-for-byte, forever. */
    @Test
    fun `golden - solver outputs match the pre-refactor snapshot`() {
        val text = ZipSolverGoldenSnapshotTest::class.java.getResourceAsStream(resourcePath)!!
            .readBytes().decodeToString()
        val solver = ZipSolver()
        for (board in parse(text)) {
            val puzzle = when (val decoded = ZipCodec.decode(Base64.getDecoder().decode(board.codecB64))) {
                is DecodeResult.Ok -> decoded.value
                is DecodeResult.Err -> throw AssertionError("${board.name}: ${decoded.error}")
            }
            // A16 cross-check: the recorded boards stay internally consistent.
            assertTrue("${board.name} verifyPath", ZipRules.verifyPath(puzzle, puzzle.solution!!))
            assertEquals("${board.name} countSolutions", board.countSolutions, solver.countSolutions(puzzle))
            assertEquals("${board.name} solve", board.solve, solver.solve(puzzle)?.map { it.idx })
        }
    }
}
