package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.game.editor.Verdict
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Opt-in measurement, not part of the gate: the numbers behind [TangoCompleter.MIN_PIECES]
 * (ticket 65 Unknowns). Run with
 * `TANGO_COMPLETER_MEASURE=true ./gradlew :games:tango:test --rerun --tests '*TangoCompleterMeasurement'`
 * (`--rerun` because Gradle does not rerun a test task for a changed environment variable).
 * TANGO_COMPLETER_SAMPLES sets the drafts per row (default 40).
 *
 * Per board size it prints one row per kind of draft, from a blank board through the minimum
 * (as a given, as a marker) to marker-heavy boards: how often and how fast `complete` finishes
 * them under the Studio's 2 s budget, how many givens the finished board holds, and how many
 * of the drafts already had one answer the hint ladder could not reach (those finish only
 * because givens are added past uniqueness). Every finished board is checked to be Unique and
 * not needs-guessing. The table is in the test's standard output
 * (`build/test-results/test/TEST-*TangoCompleterMeasurement.xml`).
 *
 * The answers the drafts are cut from generate in well under a second per size, so nothing is
 * cached; the whole run takes a few seconds.
 */
class TangoCompleterMeasurement {

    @Test
    fun `measure completions for sizes 4, 6, 8 and 10`() {
        assumeTrue(System.getenv("TANGO_COMPLETER_MEASURE") == "true")
        val samples = System.getenv("TANGO_COMPLETER_SAMPLES")?.toInt() ?: 40
        drafts(8, givens = 4, markers = 4, WARM_UP).forEachIndexed { seed, draft -> press(draft, seed) }
        println("| size | givens | markers | done | p50 ms | p95 ms | max ms | givens after | needed guessing |")
        println("|---|---|---|---|---|---|---|---|---|")
        for (n in TangoDraft.SIZES) {
            val edges = TangoDraftCuts.edgeCount(n)
            val minimum = TangoCompleter.MIN_PIECES
            val rows = listOf(
                0 to 0, minimum to 0, 0 to minimum, n / 2 to n / 2, n to 0, 0 to n,
                2 to edges / 3, 0 to edges / 2, n * n / 4 to edges / 4,
            )
            for ((givens, markers) in rows) {
                val drafts = drafts(n, givens, markers, samples)
                val presses = drafts.mapIndexed { seed, draft -> press(draft, seed) }
                val done = presses.mapNotNull { (completion, _) -> (completion as? Completion.Done)?.draft }
                done.forEach { board ->
                    val verdict = TangoDraftAnalyzer.analyze(board, Budget.Unlimited)
                    check(verdict is Verdict.Unique && !verdict.needsGuessing) { "n$n $givens givens $markers markers: $verdict" }
                }
                val micros = presses.map { it.second }.sorted()
                val neededGuessing = drafts.count { draft ->
                    (TangoDraftAnalyzer.analyze(draft, Budget.Unlimited) as? Verdict.Unique)?.needsGuessing == true
                }
                println(
                    "| $n | $givens | $markers | ${done.size}/$samples " +
                        "| ${ms(micros[samples / 2])} | ${ms(micros[(samples * 95 + 99) / 100 - 1])} | ${ms(micros.last())} " +
                        "| ${"%.1f".format(done.sumOf { it.givens.size }.toDouble() / done.size)} | $neededGuessing |",
                )
            }
        }
    }

    /** [count] drafts of width [n], each its own cut of one of [ANSWERS] generated answers in turn. */
    private fun drafts(n: Int, givens: Int, markers: Int, count: Int): List<TangoDraft> {
        val answers = List(ANSWERS) { TangoDraftCuts.answer(n, seed = n * 100L + it) }
        return List(count) { TangoDraftCuts.cut(n, answers[it % ANSWERS], givens, markers, Random(n * 1000 + it)) }
    }

    /** One press under the Studio's 2 s budget: the completion and the wall time in microseconds. */
    private fun press(draft: TangoDraft, seed: Int): Pair<Completion<TangoDraft>, Long> {
        val start = System.nanoTime()
        val completion = TangoCompleter.complete(draft, Random(seed), Budget.within(BUDGET_MS, System::nanoTime))
        return completion to (System.nanoTime() - start) / 1_000
    }

    private fun ms(micros: Long): String = "%.1f".format(micros / 1_000.0)

    private companion object {
        const val BUDGET_MS = 2_000L

        /** Presses that warm the JIT before the first measured row. */
        const val WARM_UP = 20

        /** Generated answers per size the drafts are cut from. */
        const val ANSWERS = 12
    }
}
