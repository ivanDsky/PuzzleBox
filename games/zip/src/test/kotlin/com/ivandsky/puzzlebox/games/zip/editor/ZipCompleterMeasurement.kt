package com.ivandsky.puzzlebox.games.zip.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.game.editor.FinishReadiness
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import com.ivandsky.puzzlebox.games.zip.ZipBoard
import com.ivandsky.puzzlebox.games.zip.ZipGenerator
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle
import com.ivandsky.puzzlebox.games.zip.ZipTestSupport
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Opt-in measurement, not part of the gate: the numbers behind [ZipCompleter.MIN_PATH] and the
 * limits of the completer's walk and repair (ticket 64 Unknowns). Run with
 * `ZIP_COMPLETER_MEASURE=true ./gradlew :games:zip:test --rerun --tests '*ZipCompleterMeasurement'`
 * (`--rerun` because Gradle does not rerun a test task for a changed environment variable).
 * ZIP_COMPLETER_SAMPLES sets the drafts per row (default 60).
 *
 * Per board size it prints one row per kind of draft: how often and how fast `complete`
 * finishes it under the Studio's 2 s budget, and how many numbers and walls the finished board
 * has beyond the draft's. "walled" counts the finished boards with a wall and two numbers,
 * and "walls per number" is their mix (mean, least to most; ticket 97). "path k" is the first
 * k cells of a generated board's path with one number and two of its walls, so it always has
 * a full path. "walk k" is a random k-cell walk on a blank board, the hardest thing an author
 * may draw: "blocked" counts the ones readiness blocks, so Finish is not offered on them, and
 * "no path" the ones the walk proved have no full path, which no completer can finish. "ends"
 * and "numbers" are path-less boards with the two end numbers, or with every number and wall
 * of the board. The table is in the test's standard output
 * (`build/test-results/test/TEST-*ZipCompleterMeasurement.xml`).
 *
 * The boards are generated once and kept in `build/zip-completer-boards-<size>.txt`; the run
 * takes about two minutes.
 */
class ZipCompleterMeasurement {

    @Test
    fun `measure completions for sizes 5 to 8`() {
        assumeTrue(System.getenv("ZIP_COMPLETER_MEASURE") == "true")
        val samples = System.getenv("ZIP_COMPLETER_SAMPLES")?.toInt() ?: 60
        rows(8).forEach { row -> List(WARM_UP) { row.draft(it) }.forEachIndexed { seed, draft -> press(draft, seed) } }
        println(
            "| size | draft | done | blocked | no path | p50 ms | p95 ms | max ms | numbers added | walls added " +
                "| numbers | walls | walled | walls per number |",
        )
        println("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|")
        for (size in 5..8) for (row in rows(size)) {
            val drafts = List(samples) { row.draft(it) }
            val presses = drafts.mapIndexed { seed, draft -> press(draft, seed) }
            val millis = presses.map { it.millis }.sorted()
            val done = presses.mapNotNull { it.board }
            val noPath = drafts.withIndex().count { (seed, draft) ->
                ZipPathExtension(draft, Budget.Unlimited).run { extend(Random(seed)) == null && noPath }
            }
            val blocked = drafts.count { ZipCompleter.readiness(it) is FinishReadiness.Blocked }
            val mix = done.map { it.walls.size.toDouble() / it.waypoints.size }
            println(
                "| $size | ${row.name} | ${done.size}/$samples | $blocked | $noPath " +
                    "| ${millis[samples / 2]} | ${millis[(samples * 95 + 99) / 100 - 1]} | ${millis.last()} " +
                    "| ${mean(presses) { it.numbersAdded }} | ${mean(presses) { it.wallsAdded }} " +
                    "| ${mean(presses) { it.board?.waypoints?.size }} | ${mean(presses) { it.board?.walls?.size }} " +
                    "| ${done.count { it.walls.isNotEmpty() && it.waypoints.size >= 2 }}/${done.size} " +
                    "| ${if (mix.isEmpty()) "-" else "%.2f (%.2f to %.2f)".format(mix.average(), mix.min(), mix.max())} |",
            )
        }
    }

    private class Row(val name: String, val draft: (Int) -> ZipDraft)

    private class Press(val board: ZipDraft?, val millis: Long, val numbersAdded: Int?, val wallsAdded: Int?)

    /** The kinds of draft measured for [size]; sample `i` is its own cut of one of the size's boards in turn. */
    private fun rows(size: Int): List<Row> {
        val boards = boards(size)
        val cells = size * size
        val minimum = ZipCompleter.MIN_PATH
        val short = listOf(minimum - 1, minimum, minimum + 1, minimum + 2)
        fun board(sample: Int) = boards[sample % boards.size]
        fun seeded(sample: Int) = Random(size * 1000 + sample)
        val lengths = (short.take(2) + size + cells / 2 + (cells - size)).distinct().sorted()
        return lengths.map { length ->
            Row("path $length") { ZipDraftCuts.drawn(board(it), length, marks = 1, walls = 2, seeded(it)) }
        } + (short + size + 2 * size).distinct().sorted().map { length ->
            Row("walk $length") { ZipDraftCuts.walked(size, length, seeded(it)) }
        } + listOf(
            Row("ends") { ZipDraftCuts.numbered(board(it), inner = 0, walls = 2, seeded(it)) },
            Row("ends, no walls") { ZipDraftCuts.numbered(board(it), inner = 0, walls = 0, seeded(it)) },
            Row("numbers") { ZipDraftCuts.numbered(board(it), inner = cells, walls = cells, seeded(it)) },
        )
    }

    /** The first unique boards of one generator attempt per seed from `size * 1000` up, read from the build directory when already there. */
    private fun boards(size: Int): List<ZipPuzzle> {
        val cache = File("build/zip-completer-boards-$size.txt")
        val kept = if (cache.exists()) cache.readLines() else emptyList()
        val variant = ZipTestSupport.variantOf(size)
        if (kept.size >= BOARDS) {
            return kept.take(BOARDS).map { line ->
                val (walls, numbers, answer) = line.split("|").map { cells -> cells.split(",").filter(String::isNotEmpty).map(String::toInt) }
                val board = ZipBoard(size, walls.mapTo(HashSet()) { Wall(Cell(it / 2), WallDirection.entries[it % 2]) }, numbers.map(::Cell))
                ZipPuzzle(variant.id, board, solution = answer.map(::Cell))
            }
        }
        val boards = generateSequence(size * 1000) { it + 1 }
            .filter { seed -> startsOnAPath(size, seed) }
            .mapNotNull { seed -> ZipGenerator(Random(seed)).next(variant) }
            .take(BOARDS).toList()
        cache.writeText(
            boards.joinToString("\n") { board ->
                listOf(
                    board.board.walls.map { it.cell.idx * 2 + it.direction.ordinal }.sorted(),
                    board.board.waypoints.map { it.idx },
                    checkNotNull(board.solution).map { it.idx },
                ).joinToString("|") { it.joinToString(",") }
            },
        )
        return boards
    }

    /**
     * False for a seed whose generator attempt would start its path on the minority colour of an
     * odd board. No full path starts there, and the generator's walk has no node limit: at 7×7 it
     * does not come back from trying them all. Repeats the generator's first two draws (the
     * number count, then the start cell).
     */
    private fun startsOnAPath(size: Int, seed: Int): Boolean {
        val random = Random(seed)
        random.nextInt(2)
        val start = random.nextInt(size * size)
        return size % 2 == 0 || (start / size + start % size) % 2 == 0
    }

    /** One press under the Studio's 2 s budget: the board when done, the wall time in ms, and what was added. */
    private fun press(draft: ZipDraft, seed: Int): Press {
        val start = System.nanoTime()
        val completion = ZipCompleter.complete(draft, Random(seed), Budget.within(BUDGET_MS, System::nanoTime))
        val millis = (System.nanoTime() - start) / 1_000_000
        val board = (completion as? Completion.Done)?.draft
        return Press(board, millis, board?.let { it.waypoints.size - draft.waypoints.size }, board?.let { it.walls.size - draft.walls.size })
    }

    private fun mean(presses: List<Press>, value: (Press) -> Int?): String =
        presses.mapNotNull(value).takeIf { it.isNotEmpty() }?.let { "%.1f".format(it.average()) } ?: "-"

    private companion object {
        const val BUDGET_MS = 2_000L
        const val BOARDS = 12

        /** Presses per row that warm the JIT before the first measured row. */
        const val WARM_UP = 3
    }
}
