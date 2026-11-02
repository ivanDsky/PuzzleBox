package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.games.queens.engine.solve.QueensSolver
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixed-workload benchmark gate (zip precedent: ZipSolverBenchmarkTest): `countSolutions()`
 * over the seed-deterministic golden workload — one board per configuration, official
 * sizes n8/n9/n10 at N=1 plus the N=2 synthetic n6 (the exact boards the snapshot pins).
 * Timing is REPORTED, never asserted (CI machines vary); the pre/post comparison happens
 * in the ticket report. Correctness is asserted: every board's count must match the
 * snapshot, so the gate cannot be gamed by a broken (fast but wrong) search.
 */
class QueensSolverBenchmarkTest {

    private data class Workload(val name: String, val puzzle: QueensPuzzle, val count: Int)

    private fun workload(): List<Workload> {
        val text = QueensSolverBenchmarkTest::class.java
            .getResourceAsStream("/fixtures/queens-solver-golden.json")!!.readBytes().decodeToString()
        return Json.parseToJsonElement(text).jsonObject["boards"]!!.jsonArray.map { element ->
            val o = element.jsonObject
            val size = o["size"]!!.jsonPrimitive.content.toInt()
            val q = o["queensPerUnit"]!!.jsonPrimitive.content.toInt()
            val regions = o["regions"]!!.jsonArray.map { it.jsonPrimitive.content.toInt() }
            val solution = o["solve"]!!.jsonArray.map { it.jsonPrimitive.content.toInt() }.map { Cell(it) }
            Workload(
                name = o["name"]!!.jsonPrimitive.content,
                puzzle = QueensPuzzle(size, regions, q, solution),
                count = o["countSolutions"]!!.jsonPrimitive.content.toInt(),
            )
        }
    }

    @Test
    fun `benchmark - countSolutions over the golden workload`() {
        val boards = workload()
        assertEquals(4, boards.size)
        val solver = QueensSolver()

        // Correctness first: identical counts to the snapshot.
        boards.forEach { (name, puzzle, count) ->
            assertEquals("count drift on $name", count, solver.countSolutions(puzzle))
        }

        // 2 warm-up passes, then 7 measured passes; each pass runs the workload 5x so the
        // median lands well above timer noise (single counts are ~0-100ms at n10).
        val onePass = {
            repeat(5) { boards.forEach { solver.countSolutions(it.puzzle) } }
        }
        repeat(2) { onePass() }
        val passes = mutableListOf<Long>()
        repeat(7) {
            val t0 = System.nanoTime()
            onePass()
            passes += (System.nanoTime() - t0) / 1_000_000
        }
        val median = passes.sorted()[passes.size / 2]
        println("queens benchmark: workload ${boards.map { it.name }} median ${median}ms per 5x pass (min ${passes.min()}ms, max ${passes.max()}ms)")
        // Soft sanity bound only — the report compares medians across refactors.
        assertTrue("benchmark blew the soft bound: ${median}ms", median < 30_000)
    }
}
