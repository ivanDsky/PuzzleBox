package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level
import com.ivandsky.puzzlebox.game.editor.FinishBudget
import com.ivandsky.puzzlebox.games.sudoku.KnownSolutions
import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry
import com.ivandsky.puzzlebox.games.sudoku.SudokuVariants
import com.ivandsky.puzzlebox.games.sudoku.engine.solve.SudokuRandomFill
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.Locale
import kotlin.random.Random

/**
 * Opt-in measurement, not part of the gate: the numbers behind [SudokuLevels] (ticket 92).
 * Run with
 * `SUDOKU_LEVELS_MEASURE=true ./gradlew :games:sudoku:test --rerun --tests '*SudokuLevelsMeasurement'`
 * (`--rerun` because Gradle does not rerun a test task for a changed environment variable).
 * SUDOKU_LEVELS_SAMPLES sets the drafts per row (default 100). The tables are in the test's
 * standard output (`build/test-results/test/TEST-*SudokuLevelsMeasurement.xml`); a run takes
 * under a minute.
 *
 * The first table is where boards land: per size and source, how many boards [SudokuScoredGrader]
 * puts in each level, with the spread of their scores and of their givens. The sources are the
 * LinkedIn dailies and the generated corpus (6×6 only; boards the ladder cannot finish are
 * left out), and the board Finish for me makes from one given with no target and with each.
 * The second table is Finish for me per target under the Studio's budget ([FinishBudget]):
 * from drafts cut from a random grid to a number of givens, how many presses end at the
 * target, at another level (the closest board found) or with no board, and how long they take.
 */
class SudokuLevelsMeasurement {

    @Serializable
    private data class Board(val id: String, val givens: String)

    @Test
    fun `measure levels and targeted finishes at both sizes`() {
        assumeTrue(System.getenv("SUDOKU_LEVELS_MEASURE") == "true")
        val samples = System.getenv("SUDOKU_LEVELS_SAMPLES")?.toInt() ?: 100
        repeat(WARM_UP) { seed -> for (size in SudokuDraft.SIZES) for (target in Level.entries) press(cut(size, kept = 1, seed), seed, target) }

        println("| size | boards | level | count | score: min / p50 / max | givens: min / mean / max |")
        println("|---|---|---|---|---|---|")
        spread(6, "LinkedIn dailies", corpus("sudoku-boards.json"))
        spread(6, "generated corpus", corpus("sudoku-generated.json"))
        for (size in SudokuDraft.SIZES) {
            spread(size, "Finish, no target", List(samples) { seed -> press(cut(size, kept = 1, seed), seed, target = null).done }.filterNotNull())
            for (target in Level.entries) {
                spread(size, "Finish for $target", List(samples) { seed -> press(cut(size, kept = 1, seed), seed, target).done }.filterNotNull())
            }
        }

        println()
        println("| size | draft givens | target | at the target | at another level | no board | p50 ms | p95 ms | max ms | givens after: min / mean / max |")
        println("|---|---|---|---|---|---|---|---|---|---|")
        for (size in SudokuDraft.SIZES) for (kept in KEPT.getValue(size)) for (target in Level.entries) {
            val presses = List(samples) { seed -> press(cut(size, kept, seed), seed, target) }
            val levels = presses.mapNotNull { it.done }.map { checkNotNull(label(it)).level }
            val others = levels.filter { it != target }.groupingBy { it }.eachCount().entries.joinToString(", ") { "${it.value} ${it.key}" }
            val micros = presses.map { it.micros }.sorted()
            println(
                "| $size | $kept | $target | ${levels.count { it == target }}/$samples | ${others.ifEmpty { "0" }} | ${presses.count { it.done == null }} " +
                    "| ${millis(micros[micros.size / 2])} | ${millis(micros[(micros.size * 95 + 99) / 100 - 1])} | ${millis(micros.last())} " +
                    "| ${counts(presses.mapNotNull { it.done }.map(::givens))} |",
            )
        }
    }

    /** One row per level for [boards], skipping those the scored walk cannot finish. */
    private fun spread(size: Int, source: String, boards: List<SudokuDraft>) {
        val labelled = boards.mapNotNull { board -> label(board)?.let { board to it } }
        for (level in Level.entries) {
            val at = labelled.filter { it.second.level == level }
            val scores = at.map { checkNotNull(it.second.score) }.sorted()
            val spread = if (scores.isEmpty()) "-" else "${scores.first()} / ${scores[scores.size / 2]} / ${scores.last()}"
            println("| $size | $source | $level | ${at.size}/${labelled.size} | $spread | ${counts(at.map { givens(it.first) })} |")
        }
    }

    private fun label(board: SudokuDraft): Difficulty? = SudokuScoredGrader.grade(geometry(board.size), board.givens.toIntArray(), Budget.Unlimited)

    private fun corpus(fixture: String): List<SudokuDraft> =
        JSON.decodeFromString<List<Board>>(KnownSolutions.fixture(fixture)).map { SudokuDraft(6, KnownSolutions.grid(it.givens)) }

    /** A random full grid of [size] cut down to [kept] givens; each seed its own grid and cut. */
    private fun cut(size: Int, kept: Int, seed: Int): SudokuDraft {
        val geometry = geometry(size)
        val blank = List(geometry.cellCount) { 0 }
        val grid = checkNotNull(SudokuRandomFill.fill(geometry, blank, Random(size * 100_000 + seed), Budget.Unlimited))
        return SudokuTestDrafts.cut(geometry, grid, kept, Random(seed))
    }

    /** One press for [target] under the Studio's budget for it: the board or null, and the wall time. */
    private fun press(draft: SudokuDraft, seed: Int, target: Level?): Press {
        val start = System.nanoTime()
        val completion = SudokuCompleter.complete(draft, Random(seed), Budget.within(FinishBudget.millisFor(target), System::nanoTime), target)
        return Press((completion as? Completion.Done)?.draft, (System.nanoTime() - start) / 1_000)
    }

    private fun givens(board: SudokuDraft): Int = board.givens.count { it != 0 }

    private fun counts(values: List<Int>): String =
        if (values.isEmpty()) "-" else "${values.min()} / ${String.format(Locale.ROOT, "%.1f", values.average())} / ${values.max()}"

    private fun millis(micros: Long): String = String.format(Locale.ROOT, "%.2f", micros / 1_000.0)

    private fun geometry(size: Int): SudokuGeometry = requireNotNull(SudokuVariants.bySize(size)).geometry

    private class Press(val done: SudokuDraft?, val micros: Long)

    private companion object {
        const val WARM_UP = 20
        val JSON = Json { ignoreUnknownKeys = true }

        /** Givens the author placed before pressing Finish, per size: from the minimum to a third of the board and more. */
        val KEPT = mapOf(6 to listOf(1, 4, 8, 12), 9 to listOf(1, 8, 16, 24))
    }
}
