package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensGenerator
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensVariants
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Opt-in measurement, not part of the gate: the numbers behind [QueensCompleter]'s per-size
 * minimum and its attempt limits (ticket 63 Unknowns). Run with
 * `QUEENS_COMPLETER_MEASURE=true ./gradlew :games:queens:test --rerun --tests '*QueensCompleterMeasurement'`
 * (`--rerun` because Gradle does not rerun a test task for a changed environment variable).
 * QUEENS_COMPLETER_SAMPLES sets the drafts per row (default 30).
 *
 * Per board size it prints one row for drafts cut at the minimum and one for drafts with every
 * region started, each with two crowns and with none: how often and how fast `complete`
 * finishes them under the Studio's 2 s budget, and how many single attempts reach a unique
 * board the hint ladder finishes. The table is in the test's standard output
 * (`build/test-results/test/TEST-*QueensCompleterMeasurement.xml`).
 *
 * The boards are generated once and kept in `build/queens-completer-boards-<size>.txt`. With
 * them the run takes about half a minute; a cold run first spends several minutes generating
 * the size 12 boards, where about one generator attempt in nine has a single answer.
 */
class QueensCompleterMeasurement {

    @Test
    fun `measure completions for sizes 4 to 12`() {
        assumeTrue(System.getenv("QUEENS_COMPLETER_MEASURE") == "true")
        val samples = System.getenv("QUEENS_COMPLETER_SAMPLES")?.toInt() ?: 30
        drafts(8, QueensCompleter.minimumStarted(8), crowns = 2, WARM_UP).forEachIndexed { seed, draft -> press(draft, seed) }
        println("| size | started | crowns | done | p50 ms | p95 ms | max ms | attempts | unique | ladder ok |")
        println("|---|---|---|---|---|---|---|---|---|---|")
        for (size in QueensVariants.MIN_SIZE..QueensVariants.MAX_SIZE) {
            for (started in listOf(QueensCompleter.minimumStarted(size), size)) for (crowns in listOf(2, 0)) {
                val drafts = drafts(size, started, crowns, samples)
                val presses = drafts.mapIndexed { seed, draft -> press(draft, seed) }
                val millis = presses.map { it.second }.sorted()
                val attempts = drafts.mapIndexed { seed, draft -> attempts(draft, seed) }
                println(
                    "| $size | $started | $crowns | ${presses.count { it.first }}/$samples " +
                        "| ${millis[samples / 2]} | ${millis[(samples * 95 + 99) / 100 - 1]} | ${millis.last()} " +
                        "| ${samples * ATTEMPTS} | ${attempts.sumOf { it.first }} | ${attempts.sumOf { it.second }} |",
                )
            }
        }
    }

    /** [count] drafts of [size], each its own cut of one of the size's boards in turn. */
    private fun drafts(size: Int, started: Int, crowns: Int, count: Int): List<QueensDraft> {
        val boards = boards(size)
        return List(count) { QueensDraftCuts.cut(boards[it % boards.size], started, crowns, Random(size * 1000 + it)) }
    }

    /** The first unique boards of `QueensGenerator(Random(size))`, read from the build directory when already there. */
    private fun boards(size: Int): List<QueensPuzzle> {
        val count = if (size <= QUICK_SIZE) QUICK_BOARDS else SLOW_BOARDS
        val cache = File("build/queens-completer-boards-$size.txt")
        val kept = if (cache.exists()) cache.readLines() else emptyList()
        if (kept.size >= count) {
            return kept.take(count).map { line ->
                val (regions, answer) = line.split("|").map { cells -> cells.split(",").map(String::toInt) }
                QueensPuzzle(size, regions, solution = answer.map(::Cell))
            }
        }
        val generator = QueensGenerator(Random(size))
        val boards = ArrayList<QueensPuzzle>()
        while (boards.size < count) generator.next(size)?.let { boards += it }
        cache.writeText(
            boards.joinToString("\n") { board -> board.regions.joinToString(",") + "|" + checkNotNull(board.solution).joinToString(",") { it.idx.toString() } },
        )
        return boards
    }

    /** One press: done or not, and the wall time in ms, under the Studio's 2 s budget. */
    private fun press(draft: QueensDraft, seed: Int): Pair<Boolean, Long> {
        val start = System.nanoTime()
        val completion = QueensCompleter.complete(draft, Random(seed), Budget.within(BUDGET_MS, System::nanoTime))
        return (completion is Completion.Done) to (System.nanoTime() - start) / 1_000_000
    }

    /** [ATTEMPTS] single attempts on [draft]: how many reach a unique board, and how many of those the ladder finishes. */
    private fun attempts(draft: QueensDraft, seed: Int): Pair<Int, Int> {
        val growth = QueensPinnedGrowth(draft, Budget.Unlimited)
        val random = Random(seed)
        val boards = List(ATTEMPTS) { growth.attempt(Random(random.nextLong())) }.filterNotNull()
        val finishable = boards.count { board ->
            (QueensDraftAnalyzer.analyze(board, Budget.Unlimited) as Verdict.Unique).needsGuessing.not()
        }
        return boards.size to finishable
    }

    private companion object {
        const val BUDGET_MS = 2_000L
        const val ATTEMPTS = 4

        /** Presses that warm the JIT before the first measured row. */
        const val WARM_UP = 20

        /** Up to this size a board generates in well under a second. */
        const val QUICK_SIZE = 10
        const val QUICK_BOARDS = 12
        const val SLOW_BOARDS = 8
    }
}
