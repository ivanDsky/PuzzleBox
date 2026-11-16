package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level
import com.ivandsky.puzzlebox.game.editor.FinishBudget
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Opt-in measurement, not part of the gate: the numbers behind [TangoScoreBands] and
 * [TangoTargetFinish.ROUNDS] (ticket 93). Run with
 * `TANGO_DIFFICULTY_MEASURE=true ./gradlew :games:tango:test --rerun --tests '*TangoDifficultyMeasurement'`
 * (`--rerun` because Gradle does not rerun a test task for a changed environment variable).
 * TANGO_DIFFICULTY_SAMPLES sets the drafts per kind of draft (default 10).
 *
 * It prints four tables to the test's standard output
 * (`build/test-results/test/TEST-*TangoDifficultyMeasurement.xml`):
 * 1. what a givens-only Finish can reach, per size and kind of draft: the score of the first board
 *    the ladder finishes and of that board with every added given it can spare taken back, and
 *    how each of the two samples splits over the levels;
 * 2. the 756 real 6×6 dailies per level: how many, their scores and their clue counts;
 * 3. Finish per size and target under [FinishBudget]: hits per kind of draft and the time taken;
 * 4. the boards of table 3 per level the grader gives them: scores and givens.
 * Every finished board is checked to be Unique and not needs-guessing.
 */
class TangoDifficultyMeasurement {

    @Test
    fun `measure scores, levels and Finish hits for sizes 4, 6, 8 and 10`() {
        assumeTrue(System.getenv("TANGO_DIFFICULTY_MEASURE") == "true")
        val samples = System.getenv("TANGO_DIFFICULTY_SAMPLES")?.toInt() ?: 10
        for (target in Level.entries) drafts(8, 4, 4, WARM_UP).forEachIndexed { seed, draft -> press(draft, seed, target) }
        reach(samples)
        dailies()
        finish(samples)
    }

    private fun reach(samples: Int) {
        println("\n1. What Finish can reach (score p5/p50/p95/max; givens p50; boards per level Easy/Medium/Hard/Expert)")
        println("| size | givens | markers | first board score | givens | levels | pared board score | givens | levels |")
        println("|---|---|---|---|---|---|---|---|---|")
        for (n in TangoDraft.SIZES) {
            for ((givens, markers) in kinds(n)) {
                val first = ArrayList<Pair<Int, Int>>()
                val pared = ArrayList<Pair<Int, Int>>()
                drafts(n, givens, markers, samples * REACH_ROUNDS).forEachIndexed { seed, draft ->
                    val (one, other) = firstAndPared(draft, Random(seed)) ?: return@forEachIndexed
                    first += one
                    pared += other
                }
                println(
                    "| $n | $givens | $markers | ${spread(first.map { it.first })} | ${median(first.map { it.second })} | ${split(n, first)} " +
                        "| ${spread(pared.map { it.first })} | ${median(pared.map { it.second })} | ${split(n, pared)} |",
                )
            }
        }
    }

    /** How many of [boards] (score to givens) of width [n] are at each level, Easy first. */
    private fun split(n: Int, boards: List<Pair<Int, Int>>): String {
        val levels = boards.groupingBy { TangoScoreBands.of(n).levelOf(it.first) }.eachCount()
        return Level.entries.joinToString("/") { (levels[it] ?: 0).toString() }
    }

    /** Score and givens of the first board the ladder finishes for [draft] and of it pared to the givens it needs. */
    private fun firstAndPared(draft: TangoDraft, random: Random): Pair<Pair<Int, Int>, Pair<Int, Int>>? {
        val pieces = TangoPuzzle(TangoDraft.variantOf(draft.n), draft.n, draft.givens, draft.markers)
        val answer = TangoPinnedFill.fill(pieces, random, Budget.Unlimited) ?: return null
        val walk = TangoSolveWalk(draft.n, draft.markers)
        val givens = draft.givens.toMutableMap()
        var tally = walk.tally(givens, Budget.Unlimited)!!
        while (tally.left > 0) {
            val cell = walk.stuck().random(random)
            givens[cell] = answer[cell]
            tally = walk.tally(givens, Budget.Unlimited)!!
        }
        if (tally.hardest == null) return null
        val first = TangoScoredGrader.score(draft.n, tally) to givens.size
        for (cell in (givens.keys - draft.givens.keys).shuffled(random)) {
            givens.remove(cell)
            val lighter = walk.tally(givens, Budget.Unlimited)!!
            if (lighter.left > 0) givens[cell] = answer[cell] else tally = lighter
        }
        return first to (TangoScoredGrader.score(draft.n, tally) to givens.size)
    }

    private fun dailies() {
        println("\n2. The 756 real 6×6 dailies per level")
        println("| level | boards | score min/p50/max | givens p50 (min–max) | markers p50 (min–max) | line-fills steps p50 | narrow steps p50 |")
        println("|---|---|---|---|---|---|---|")
        val graded = TangoDailyBoards.all.map { board ->
            val tally = TangoSolveWalk(board.n, board.markers).tally(board.givens, Budget.Unlimited)!!
            Triple(board, tally, TangoScoredGrader.difficulty(board.n, tally)!!)
        }
        for (level in Level.entries) {
            val at = graded.filter { it.third.level == level }
            if (at.isEmpty()) continue
            println(
                "| $level | ${at.size} (${at.size * 100 / graded.size}%) | ${range(at.map { it.third.score!! })} " +
                    "| ${counts(at.map { it.first.givens.size })} | ${counts(at.map { it.first.markers.size })} " +
                    "| ${median(at.map { it.second.lineFills })} | ${median(at.map { it.second.narrow })} |",
            )
        }
        println("all dailies: score ${spread(graded.map { it.third.score!! })}")
    }

    private fun finish(samples: Int) {
        val boards = ArrayList<Triple<Int, Level, TangoDraft>>()
        println("\n3. Finish per size and target (hits per kind of draft, of $samples)")
        println("| size | target | budget ms | ${KIND_NAMES.joinToString(" | ")} | hit | done | p50 ms | p95 ms | max ms |")
        println("|---|---|---|${KIND_NAMES.joinToString("") { "---|" }}---|---|---|---|---|")
        for (n in TangoDraft.SIZES) {
            for (target in Level.entries) {
                val micros = ArrayList<Long>()
                var done = 0
                val hits = kinds(n).map { (givens, markers) ->
                    drafts(n, givens, markers, samples).withIndex().count { (seed, draft) ->
                        val (completion, took) = press(draft, seed, target)
                        micros += took
                        val board = (completion as? Completion.Done)?.draft ?: return@count false
                        done++
                        val verdict = TangoDraftAnalyzer.analyze(board, Budget.Unlimited)
                        check(verdict is Verdict.Unique && !verdict.needsGuessing) { "n$n $givens givens $markers markers: $verdict" }
                        val level = TangoScoredGrader.grade(board, verdict.solution, Budget.Unlimited)?.level
                        if (level != null) boards += Triple(n, level, board)
                        level == target
                    }
                }
                micros.sort()
                val total = micros.size
                println(
                    "| $n | $target | ${FinishBudget.millisFor(target)} | ${hits.joinToString(" | ")} | ${hits.sum()}/$total | $done/$total " +
                        "| ${ms(micros[total / 2])} | ${ms(micros[(total * 95 + 99) / 100 - 1])} | ${ms(micros.last())} |",
                )
            }
        }
        println("\n4. Finished boards per level the grader gives them")
        println("| size | level | boards | score min/p50/max | givens p50 (min–max) |")
        println("|---|---|---|---|---|")
        for (n in TangoDraft.SIZES) {
            for (level in Level.entries) {
                val at = boards.filter { it.first == n && it.second == level }.map { it.third }
                if (at.isEmpty()) continue
                val scores = at.map { TangoScoredGrader.score(n, TangoSolveWalk(n, it.markers).tally(it.givens, Budget.Unlimited)!!) }
                println("| $n | $level | ${at.size} | ${range(scores)} | ${counts(at.map { it.givens.size })} |")
            }
        }
    }

    /** The kinds of draft measured, as givens to markers: from one given to a marker-heavy board. */
    private fun kinds(n: Int): List<Pair<Int, Int>> {
        val edges = TangoDraftCuts.edgeCount(n)
        return listOf(1 to 0, n to 0, 0 to n / 2, n / 2 to n / 2, 0 to n, 2 to edges / 6, 2 to edges / 3)
    }

    /** [count] drafts of width [n], each its own cut of one of [ANSWERS] generated answers in turn. */
    private fun drafts(n: Int, givens: Int, markers: Int, count: Int): List<TangoDraft> {
        val answers = List(ANSWERS) { TangoDraftCuts.answer(n, seed = n * 100L + it) }
        return List(count) { TangoDraftCuts.cut(n, answers[it % ANSWERS], givens, markers, Random(n * 1000 + it)) }
    }

    /** One press under the Studio's budget for [target]: the completion and the wall time in microseconds. */
    private fun press(draft: TangoDraft, seed: Int, target: Level): Pair<Completion<TangoDraft>, Long> {
        val start = System.nanoTime()
        val budget = Budget.within(FinishBudget.millisFor(target), System::nanoTime)
        val completion = TangoCompleter.complete(draft, Random(seed), budget, target)
        return completion to (System.nanoTime() - start) / 1_000
    }

    private fun spread(values: List<Int>): String {
        if (values.isEmpty()) return "none"
        val sorted = values.sorted()
        fun at(percent: Int) = sorted[(sorted.size * percent / 100).coerceAtMost(sorted.size - 1)]
        return "${at(5)}/${at(50)}/${at(95)}/${sorted.last()}"
    }

    private fun range(values: List<Int>): String = "${values.min()}/${median(values)}/${values.max()}"

    private fun counts(values: List<Int>): String = "${median(values)} (${values.min()}–${values.max()})"

    private fun median(values: List<Int>): Int = values.sorted()[values.size / 2]

    private fun ms(micros: Long): String = "%.1f".format(micros / 1_000.0)

    private companion object {
        val KIND_NAMES = listOf("1 given", "n givens", "n/2 markers", "n/2 + n/2", "n markers", "2 + 1/6 edges", "2 + 1/3 edges")

        /** Presses per target that warm the JIT before the first measured row. */
        const val WARM_UP = 10

        /** Generated answers per size the drafts are cut from. */
        const val ANSWERS = 12

        /** Table 1 walks this many boards per sampled draft: one walk is a fraction of a Finish. */
        const val REACH_ROUNDS = 20
    }
}
