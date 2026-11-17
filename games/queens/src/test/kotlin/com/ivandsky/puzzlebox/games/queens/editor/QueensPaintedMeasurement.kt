package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Opt-in measurement, not part of the gate: Finish for me on drafts that look like an author's
 * (ticket 63b). Run with
 * `QUEENS_PAINTED_MEASURE=true ./gradlew :games:queens:test --rerun --tests '*QueensPaintedMeasurement'`.
 * QUEENS_PAINTED_SAMPLES sets the drafts per row (default 20), QUEENS_PAINTED_SIZES the sizes
 * (default `5,6,7,8,9,10`), QUEENS_PAINTED_TARGET the level every press asks for, as the Studio
 * does (ticket 107: paint first, crowns second); unset is "any", the order before 107.
 *
 * Drafts come from two sources. "unique" is a generated unique board, so a completion that keeps
 * every painted cell exists. "painting" is a hand-style painting with two answers or more
 * ([QueensPaintings]), where it may not. Each is cut back to a share of painted cells
 * ([QueensDraftCuts.painted]); 100 is the fully painted board. Crowns are the whole intended
 * answer or none.
 *
 * Per row: how many presses finish under the Studio's 2 s budget and how fast; "kept" is the
 * presses that changed no painted cell, "changed" the most painted cells one press changed,
 * "crowns moved" the presses that moved a crown. Every finished board is checked: Unique, the
 * hint ladder finishes it, no split region. The table is in the test's standard output
 * (`build/test-results/test/TEST-*QueensPaintedMeasurement.xml`).
 */
class QueensPaintedMeasurement {

    @Test
    fun `measure painted drafts for sizes 5 to 10`() {
        assumeTrue(System.getenv("QUEENS_PAINTED_MEASURE") == "true")
        val samples = System.getenv("QUEENS_PAINTED_SAMPLES")?.toInt() ?: 20
        val sizes = (System.getenv("QUEENS_PAINTED_SIZES") ?: "5,6,7,8,9,10").split(",").map(String::toInt)
        val unsound = ArrayList<String>()
        drafts("unique", 8, 70, crowned = true, WARM_UP).forEachIndexed { seed, draft -> press(draft, seed) }
        println("| source | size | painted % | crowns | done | kept | changed | crowns moved | p50 ms | p95 ms | max ms |")
        println("|---|---|---|---|---|---|---|---|---|---|---|")
        for (source in listOf("unique", "painting")) for (size in sizes) {
            for (share in if (source == "unique") SHARES else SHARES + 100) for (crowned in listOf(true, false)) {
                val drafts = drafts(source, size, share, crowned, samples)
                val presses = drafts.mapIndexed { seed, draft -> press(draft, seed) }
                val done = presses.mapIndexedNotNull { index, press -> press.first?.let { drafts[index] to it } }
                val changed = done.map { (draft, board) -> draft.regions.indices.count { draft.regions[it] != QueensDraft.UNPAINTED && draft.regions[it] != board.regions[it] } }
                done.filterNot { sound(it.second) }.mapTo(unsound) { "$source n$size $share% ${it.second}" }
                val millis = presses.map { it.second }.sorted()
                println(
                    "| $source | $size | $share | ${if (crowned) "all" else "none"} | ${done.size}/$samples " +
                        "| ${changed.count { it == 0 }} | ${changed.maxOrNull() ?: "-"} | ${done.count { it.first.crowns != it.second.crowns }} " +
                        "| ${millis[samples / 2]} | ${millis[(samples * 95 + 99) / 100 - 1]} | ${millis.last()} |",
                )
            }
        }
        assertEquals(emptyList<String>(), unsound)
    }

    /** [count] drafts: each a [share] percent cut of one of the source's boards in turn. */
    private fun drafts(source: String, size: Int, share: Int, crowned: Boolean, count: Int): List<QueensDraft> {
        val boards = boards.getOrPut(source to size) { boards(source, size) }
        return List(count) { QueensDraftCuts.painted(boards[it % boards.size], share / 100.0, crowned, Random(size * 1000 + share + it)) }
    }

    private val boards = HashMap<Pair<String, Int>, List<QueensPuzzle>>()

    private fun boards(source: String, size: Int): List<QueensPuzzle> {
        val random = Random(size)
        return List(BOARDS) { if (source == "painting") QueensPaintings.ambiguous(size, random) else QueensPaintings.unique(size, random) }
    }

    /** One press under the Studio's 2 s budget: the finished board or null, and the wall time in ms. */
    private fun press(draft: QueensDraft, seed: Int): Pair<QueensDraft?, Long> {
        val target = System.getenv("QUEENS_PAINTED_TARGET")?.let(Difficulty.Level::valueOf)
        val start = System.nanoTime()
        val completion = QueensCompleter.complete(draft, Random(seed), Budget.within(BUDGET_MS, System::nanoTime), target)
        return (completion as? Completion.Done)?.draft to (System.nanoTime() - start) / 1_000_000
    }

    private fun sound(board: QueensDraft): Boolean {
        val verdict = QueensDraftAnalyzer.analyze(board, Budget.Unlimited)
        return verdict is Verdict.Unique && !verdict.needsGuessing && verdict.solution.values.containsAll(board.crowns) &&
            QueensContiguity.splitRegions(board).isEmpty()
    }

    private companion object {
        const val BUDGET_MS = 2_000L
        const val BOARDS = 12

        /** Presses that warm the JIT before the first measured row. */
        const val WARM_UP = 20

        /** Percent of cells painted, below the fully painted 100. */
        val SHARES = listOf(40, 70, 95)
    }
}
