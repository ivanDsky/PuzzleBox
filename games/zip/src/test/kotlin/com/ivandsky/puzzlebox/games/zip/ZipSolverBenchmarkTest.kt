package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.zip.codec.ZipCodec
import com.ivandsky.puzzlebox.games.zip.engine.ZipSolver
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fixed-workload benchmark gate (A18 §2): `countSolutions()` over the 20 generated n7
 * golden boards — the exact boards the snapshot pins. Timing is REPORTED, never
 * asserted (CI machines vary); the pre/post comparison happens in the ticket report.
 * Correctness is asserted: every board's count must match the snapshot, so the gate
 * cannot be gamed by a broken (fast but wrong) search.
 */
class ZipSolverBenchmarkTest {

    private fun boards(): List<Triple<String, com.ivandsky.puzzlebox.games.zip.ZipPuzzle, Int>> {
        val text = ZipSolverBenchmarkTest::class.java
            .getResourceAsStream("/fixtures/zip-solver-golden.json")!!.readBytes().decodeToString()
        return Json.parseToJsonElement(text).jsonObject["boards"]!!.jsonArray.mapNotNull { element ->
            val o = element.jsonObject
            if (o["name"]!!.jsonPrimitive.content == "daily-592") return@mapNotNull null // generated workload only
            val puzzle = when (val decoded = ZipCodec.decode(Base64.getDecoder().decode(o["codec"]!!.jsonPrimitive.content))) {
                is DecodeResult.Ok -> decoded.value
                is DecodeResult.Err -> throw AssertionError(decoded.error.toString())
            }
            Triple(o["name"]!!.jsonPrimitive.content, puzzle, o["countSolutions"]!!.jsonPrimitive.content.toInt())
        }
    }

    @Test
    fun `benchmark - countSolutions over 20 generated n7 boards`() {
        val workload = boards()
        assertEquals(20, workload.size)
        val solver = ZipSolver()

        // Correctness first: identical counts to the snapshot.
        workload.forEach { (name, puzzle, count) ->
            assertEquals("count drift on $name", count, solver.countSolutions(puzzle))
        }

        // 2 warm-up passes, then 7 measured passes; each pass runs the workload 5x so the
        // median lands well above timer noise (single counts are ~0-20ms).
        val onePass = {
            repeat(5) { workload.forEach { solver.countSolutions(it.second) } }
        }
        repeat(2) { onePass() }
        val passes = mutableListOf<Long>()
        repeat(7) {
            val t0 = System.nanoTime()
            onePass()
            passes += (System.nanoTime() - t0) / 1_000_000
        }
        val sorted = passes.sorted()
        println("ZIP-BENCH passes_ms=$passes median=${sorted[sorted.size / 2]} total_ms=${passes.sum()}")
    }
}
