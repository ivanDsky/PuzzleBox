package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level
import com.ivandsky.puzzlebox.game.editor.FinishBudget
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.queens.QueensGenerator
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensVariants
import com.ivandsky.puzzlebox.games.queens.engine.solve.QueensSearch
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Opt-in measurement, not part of the gate: the numbers behind [QueensScoredGrader.Bands] and
 * the hit rate of Finish for me aiming for a level (91). Run one table at a time with
 * `QUEENS_SCORED_MEASURE=spread` or `=finish`, as in
 * `QUEENS_SCORED_MEASURE=finish ./gradlew :games:queens:test --rerun --tests '*QueensScoredMeasurement'`
 * (`--rerun` because Gradle does not rerun a test task for a changed environment variable).
 * QUEENS_SCORED_SIZES (`7,8,9`; default every size) picks the sizes and QUEENS_SCORED_SAMPLES
 * the drafts per row of the finish table (default 20). The tables are in the test's standard
 * output (`build/test-results/test/TEST-*QueensScoredMeasurement.xml`).
 *
 * - spread: per size, how the scores of three kinds of board fall over the levels. Daily: the
 *   806 boards of the hint corpus. Generated: [QueensGenerator]'s boards. Filled: what
 *   Finish for me draws from, every fill of drafts at the minimum that has one answer the hint
 *   ladder reaches. About four minutes; the generator and the grading of its boards take most.
 * - finish: per size and target, `complete` under the Studio's budget for that target on drafts
 *   cut at the minimum and on drafts half painted: how many came back at the target, at which
 *   levels the others came back, and the wall time. Up to fifteen minutes for every size.
 */
class QueensScoredMeasurement {

    private val sizes: List<Int> =
        System.getenv("QUEENS_SCORED_SIZES")?.split(",")?.map(String::toInt) ?: (QueensVariants.MIN_SIZE..QueensVariants.MAX_SIZE).toList()

    @Test
    fun `measure the score spread of daily, generated and filled boards`() {
        assumeTrue(System.getenv("QUEENS_SCORED_MEASURE") == "spread")
        val dailies = QueensLevelBoards.dailies()
        dailies.take(WARM_UP).forEach { QueensScoredGrader.grade(it, Budget.Unlimited) }
        println("| boards | size | graded | score min | p10 | p50 | p90 | max | Easy | Medium | Hard | Expert | grade p50 ms | p95 ms |")
        println("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|")
        row("daily", "all", dailies)
        for (size in sizes) {
            row("daily", "$size", dailies.filter { it.size == size })
            row("generated", "$size", generated(size))
            row("filled", "$size", filled(size))
        }
    }

    @Test
    fun `measure how often Finish for me hits each level`() {
        assumeTrue(System.getenv("QUEENS_SCORED_MEASURE") == "finish")
        val samples = System.getenv("QUEENS_SCORED_SAMPLES")?.toInt() ?: 20
        drafts(8, half = false, WARM_UP).forEachIndexed { seed, draft -> press(draft, seed, Level.entries[seed % Level.entries.size]) }
        println("| size | draft | target | budget ms | at target | came back as | gave up | p50 ms | p95 ms | max ms |")
        println("|---|---|---|---|---|---|---|---|---|---|")
        for (size in sizes) for (half in listOf(false, true)) {
            val drafts = drafts(size, half, samples)
            for (target in Level.entries) {
                val presses = drafts.mapIndexed { seed, draft -> press(draft, seed, target) }
                val levels = presses.mapNotNull { it.first }
                val millis = presses.map { it.second }.sorted()
                println(
                    "| $size | ${if (half) "half painted" else "minimum"} | $target | ${FinishBudget.millisFor(target)} " +
                        "| ${levels.count { it == target }}/$samples | ${Level.entries.joinToString(" ") { level -> "${level.name.first()}${levels.count { it == level }}" }} " +
                        "| ${presses.count { it.first == null }} | ${millis[samples / 2]} | ${millis[(samples * 95 + 99) / 100 - 1]} | ${millis.last()} |",
                )
            }
        }
    }

    /** One table row for [boards]: their scores and levels, and what grading one costs. */
    private fun row(kind: String, size: String, boards: List<QueensPuzzle>) {
        if (boards.isEmpty()) return
        val nanos = ArrayList<Long>()
        val grades = boards.mapNotNull { board ->
            val start = System.nanoTime()
            QueensScoredGrader.grade(board, Budget.within(GRADE_BUDGET_MS, System::nanoTime)).also { nanos += System.nanoTime() - start }
        }
        if (grades.isEmpty()) return println("| $kind | $size | 0/${boards.size} |")
        val scores = grades.map { checkNotNull(it.score) }.sorted()
        fun at(share: Double): Int = scores[minOf(scores.size - 1, (share * scores.size).toInt())]
        val levels = Level.entries.joinToString(" | ") { level -> "%.1f%%".format(grades.count { it.level == level } * 100.0 / grades.size) }
        nanos.sort()
        fun millis(share: Double): String = "%.1f".format(nanos[minOf(nanos.size - 1, (share * nanos.size).toInt())] / 1e6)
        println(
            "| $kind | $size | ${grades.size}/${boards.size} | ${scores.first()} | ${at(0.1)} | ${at(0.5)} | ${at(0.9)} | ${scores.last()} " +
                "| $levels | ${millis(0.5)} | ${millis(0.95)} |",
        )
    }

    /** [QueensGenerator]'s boards with one answer the hint ladder reaches: up to [GENERATED] of them, as many as [GENERATE_MS] gives. */
    private fun generated(size: Int): List<QueensPuzzle> {
        val generator = QueensGenerator(Random(size))
        val boards = ArrayList<QueensPuzzle>()
        val end = System.nanoTime() + GENERATE_MS * 1_000_000
        val wanted = if (size <= QUICK_SIZE) GENERATED else SLOW_GENERATED
        while (boards.size < wanted && System.nanoTime() < end) generator.next(size)?.takeIf(::finishable)?.let { boards += it }
        return boards
    }

    /** Every finishable board among [FILLS] fills of each of [FILL_DRAFTS] drafts at the minimum. */
    private fun filled(size: Int): List<QueensPuzzle> = drafts(size, half = false, FILL_DRAFTS).flatMapIndexed { seed, draft ->
        val growth = QueensPinnedGrowth(draft, Budget.Unlimited)
        val random = Random(seed)
        List(FILLS) { growth.attempt(Random(random.nextLong())) }.filterNotNull().map { QueensPuzzle(size, it.regions) }.filter(::finishable)
    }

    private fun finishable(board: QueensPuzzle): Boolean =
        (QueensDraftAnalyzer.analyze(QueensDraft(board.size, board.regions), Budget.Unlimited) as? Verdict.Unique)?.needsGuessing == false

    /**
     * [count] drafts of [size], each its own cut of one of [BASES] boards in turn: at the minimum
     * of started regions with a crown on every other draft, or [half] painted with no crown. The
     * boards are fills of the blank board, which take milliseconds at every size.
     */
    private fun drafts(size: Int, half: Boolean, count: Int): List<QueensDraft> {
        val growth = QueensPinnedGrowth(QueensDraft(size, List(size * size) { QueensDraft.UNPAINTED }), Budget.Unlimited)
        val random = Random(size)
        val bases = generateSequence { Random(random.nextLong()) }.mapNotNull(growth::attempt).map { QueensPuzzle(size, it.regions) }.filter(::finishable)
            .map { it.copy(solution = QueensSearch.solutions(it, limit = 1).single()) }.take(BASES).toList()
        return List(count) { index ->
            val base = bases[index % bases.size]
            val seeded = Random(size * 1000 + index)
            if (half) QueensDraftCuts.painted(base, share = 0.5, crowned = false, seeded) else QueensDraftCuts.cut(base, QueensCompleter.minimumStarted(size), crowns = index % 2, seeded)
        }
    }

    /** One press aiming for [target] under the Studio's budget for it: the level that came back (null: gave up) and the wall time in ms. */
    private fun press(draft: QueensDraft, seed: Int, target: Level): Pair<Level?, Long> {
        val start = System.nanoTime()
        val completion = QueensCompleter.complete(draft, Random(seed), Budget.within(FinishBudget.millisFor(target), System::nanoTime), target)
        val millis = (System.nanoTime() - start) / 1_000_000
        val level: Difficulty.Level? = (completion as? Completion.Done)?.let { done ->
            checkNotNull(QueensScoredGrader.grade(QueensPuzzle(draft.size, done.draft.regions), Budget.Unlimited)).level
        }
        return level to millis
    }

    private companion object {
        /** Grades and presses that warm the JIT before the first measured row. */
        const val WARM_UP = 40

        /** A generated board's grade may take seconds at size 10 and up; one that takes longer counts as not graded. */
        const val GRADE_BUDGET_MS = 10_000L

        const val GENERATE_MS = 20_000L
        const val GENERATED = 60

        /** Up to this size a board generates in well under a second. */
        const val QUICK_SIZE = 10
        const val SLOW_GENERATED = 6

        const val BASES = 8
        const val FILL_DRAFTS = 10
        const val FILLS = 48
    }
}
