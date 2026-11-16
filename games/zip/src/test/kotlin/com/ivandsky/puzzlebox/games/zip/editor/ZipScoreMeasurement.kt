package com.ivandsky.puzzlebox.games.zip.editor

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level
import com.ivandsky.puzzlebox.game.editor.FinishBudget
import com.ivandsky.puzzlebox.game.editor.FinishReadiness
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.zip.ZipGenerator
import com.ivandsky.puzzlebox.games.zip.ZipTestSupport
import com.ivandsky.puzzlebox.games.zip.codec.ZipWireParser
import com.ivandsky.puzzlebox.games.zip.feed.TrailGameExtractor
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import kotlin.random.Random

/**
 * Opt-in measurement, not part of the gate: the numbers behind [ZipScoredGrader.BANDS] and the
 * hit rate of Finish for me aimed at a level (ticket 94). Run with
 * `ZIP_SCORE_MEASURE=true ./gradlew :games:zip:test --rerun --tests '*ZipScoreMeasurement'`
 * (`--rerun` because Gradle does not rerun a test task for a changed environment variable).
 * ZIP_SCORE_SAMPLES sets the boards per row (default 30) and ZIP_SCORE_SIZES the sizes
 * (default `5,6,7,8`; all four take about ten minutes).
 *
 * The first table is what boards score: the captured LinkedIn dailies, the app's generated
 * boards, and Finish for me with no level asked. The second is one row per size and level asked:
 * a two-cell stroke on a blank board, finished under the Studio's own budget
 * ([FinishBudget.millisFor]), with how many boards came back at the level, what the others came
 * back as, the numbers and walls on the boards, and the time a press took. Both are in the
 * test's standard output (`build/test-results/test/TEST-*ZipScoreMeasurement.xml`).
 */
class ZipScoreMeasurement {

    @Test
    fun `measure scores and level hits for sizes 5 to 8`() {
        assumeTrue(System.getenv("ZIP_SCORE_MEASURE") == "true")
        val samples = System.getenv("ZIP_SCORE_SAMPLES")?.toInt() ?: 30
        val sizes = (System.getenv("ZIP_SCORE_SIZES") ?: "5,6,7,8").split(",").map { it.trim().toInt() }
        repeat(WARM_UP) { seed -> Level.entries.forEach { press(drafts(7, WARM_UP)[seed], seed, it) } }

        println("| boards | count | numbers | walls | score min | p10 | p50 | p90 | max | Easy | Medium | Hard | Expert |")
        println("|---|---|---|---|---|---|---|---|---|---|---|---|---|")
        for ((name, file) in DAILIES) spread(name, listOf(daily(file)))
        for (size in sizes) {
            spread("$size generated", generated(size))
            spread("$size Finish, no level", drafts(size, samples).mapIndexedNotNull { seed, draft -> press(draft, seed, null).board })
        }

        println()
        println("| size | level asked | at level | came back as | numbers | walls | walls per number | p50 ms | p95 ms | max ms |")
        println("|---|---|---|---|---|---|---|---|---|---|")
        for (size in sizes) for (target in Level.entries) {
            val presses = drafts(size, samples).mapIndexed { seed, draft -> press(draft, seed, target) }
            val boards = presses.mapNotNull { it.board }
            val levels = boards.map { score(it)?.level }
            val millis = presses.map { it.millis }.sorted()
            val reached = Level.entries.filter { it in levels }.joinToString(" ") { level -> "$level ${levels.count { it == level }}" }
            val gaveUp = (presses.size - boards.size).takeIf { it > 0 }?.let { " gave up $it" }.orEmpty()
            println(
                "| $size | $target | ${levels.count { it == target }}/${presses.size} | $reached$gaveUp " +
                    "| ${mean(boards.map { it.waypoints.size })} | ${mean(boards.map { it.walls.size })} " +
                    "| ${"%.2f".format(boards.sumOf { it.walls.size }.toDouble() / boards.sumOf { it.waypoints.size })} " +
                    "| ${millis[millis.size / 2]} | ${millis[(millis.size * 95 / 100).coerceAtMost(millis.lastIndex)]} | ${millis.last()} |",
            )
        }
    }

    private fun spread(name: String, boards: List<ZipDraft>) {
        val graded = boards.mapNotNull(::score)
        val scores = graded.mapNotNull { it.score }.sorted()
        fun at(percent: Int) = scores[(scores.size - 1) * percent / 100]
        val levels = Level.entries.joinToString(" | ") { level -> graded.count { it.level == level }.toString() }
        println(
            "| $name | ${boards.size} | ${mean(boards.map { it.waypoints.size })} | ${mean(boards.map { it.walls.size })} " +
                "| ${scores.first()} | ${at(10)} | ${at(50)} | ${at(90)} | ${scores.last()} | $levels |",
        )
    }

    private fun mean(values: List<Int>): String = "%.1f".format(values.average())

    /** The scored label the Studio would show for [board], a finished board. */
    private fun score(board: ZipDraft): Difficulty? {
        val verdict = ZipDraftAnalyzer.analyze(board, Budget.Unlimited) as? Verdict.Unique ?: return null
        return ZipScoredGrader.grade(board, verdict.solution, Budget.Unlimited)
    }

    /** One press under the Studio's budget for [target]: the board when done and the wall time in ms. */
    private fun press(draft: ZipDraft, seed: Int, target: Level?): Press {
        val start = System.nanoTime()
        val completion = ZipCompleter.complete(draft, Random(seed), Budget.within(FinishBudget.millisFor(target), System::nanoTime), target)
        val millis = (System.nanoTime() - start) / NANOS_PER_MILLI
        val board = (completion as? Completion.Done)?.draft
        check(board == null || ZipDraftAnalyzer.analyze(board, Budget.Unlimited) == Verdict.Unique(Solution(board.path))) { "not unique: $board" }
        return Press(board, millis)
    }

    private class Press(val board: ZipDraft?, val millis: Long)

    /** Two-cell strokes on a blank board that Finish is offered on: what an author starts from. */
    private fun drafts(size: Int, count: Int): List<ZipDraft> {
        val random = Random(size)
        return generateSequence { ZipDraftCuts.walked(size, ZipCompleter.MIN_PATH, random) }
            .filter { ZipCompleter.readiness(it) == FinishReadiness.Ready }
            .take(count).toList()
    }

    /**
     * The app's own generated boards: the first [GENERATED] unique ones from seed `size * 1000` up,
     * the boards `ZipCompleterMeasurement` uses. Seeds that start the generator on the minority
     * colour of an odd board are skipped, and no more are asked for: its walk has no node limit,
     * and from some later seeds it does not come back.
     */
    private fun generated(size: Int): List<ZipDraft> =
        generateSequence(size * SEEDS_PER_SIZE) { it + 1 }
            .filter { seed ->
                val random = Random(seed)
                random.nextInt(2)
                val start = random.nextInt(size * size)
                size % 2 == 0 || (start / size + start % size) % 2 == 0
            }
            .mapNotNull { seed -> ZipGenerator(Random(seed)).next(ZipTestSupport.variantOf(size)) }
            .take(GENERATED)
            .map { board -> ZipDraft(size, board.board.walls, checkNotNull(board.solution).map { it.idx }, board.board.waypoints.map { it.idx }) }
            .toList()

    private fun daily(file: String): ZipDraft {
        val body = File(file).readText()
        val json = TrailGameExtractor.extract(body)?.trailGamePuzzleJson ?: body
        val board = (ZipWireParser.parse(json, LocalDate.of(2026, 1, 1)) as DecodeResult.Ok).value.puzzle.board
        return ZipDraft(board.n, board.walls, emptyList(), board.waypoints.map { it.idx })
    }

    private companion object {
        const val WARM_UP = 5
        const val SEEDS_PER_SIZE = 1_000
        const val GENERATED = 12
        const val NANOS_PER_MILLI = 1_000_000

        /** The captured LinkedIn boards; the test runs in `games/zip`. */
        const val POSTS = "../../core/network/src/test/resources/fixtures/linkedin-post"
        val DAILIES = listOf(
            "6 daily #1" to "src/test/resources/fixtures/02-byid-gt6-1.json",
            "6 daily #592" to "src/test/resources/fixtures/zip-daily-592.json",
            "6 daily, 12 walls" to "$POSTS/zip-page-daily-unsolved.rsc",
            "7 daily, no walls" to "$POSTS/zip-page-test-account-tutorial-2.rsc",
            "8 daily, 16 walls" to "$POSTS/zip-page-daily-solved.rsc",
        )
    }
}
