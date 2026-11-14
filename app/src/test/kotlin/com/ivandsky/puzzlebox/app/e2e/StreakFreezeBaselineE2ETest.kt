package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTextExactly
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.play.QueensPlayFixture
import com.ivandsky.puzzlebox.app.play.TangoPlayFixture
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensPuzzleRef
import com.ivandsky.puzzlebox.games.queensui.QueensPlayController
import com.ivandsky.puzzlebox.games.strands.StrandsNumber
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzleRef
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzleRef
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoPuzzleRef
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.PlayKey
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The streak freeze the user approved (repro R2–R7, dark theme), pinned through the real app shell.
 * Today is Fri Nov 20 and every daily game was solved each day of a 40-day run before a scenario's gap, so 4
 * freezes are held when the gap opens. Tablet (rail) shows the Home pane, the Result side card and the Tango panel;
 * the phone card is pinned on its own. Each test reads rendered nodes only, so it holds however the streak is derived.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w1280dp-h800dp-land")
class StreakFreezeBaselineE2ETest {

    @Test
    fun r2_oneMissedDay_streakHolds_oneFreezeSpent_thursdayFrozen() =
        homePane(lastSolved = TODAY.minusDays(2)) {
            awaitPane(streak = "40", best = "Best 40", freezes = "3 freezes")
            assertEquals("Thursday frozen", 1, frozenDays(HOME_PANE_WEEK))
        }

    @Test
    @Config(qualifiers = "w411dp-h891dp-420dpi")
    fun r2_phoneCard_oneMissedDay_streakHolds_oneFreezeSpent_thursdayFrozen() =
        shellRobot(tabAt(HomeKey), graph = graphWith(history(TODAY.minusDays(41), TODAY.minusDays(2))), darkTheme = true) {
            awaitTextIn(HOME_CARD, "40 days in a row · Best 40")
            assertEquals("3 freezes beside the line", 1, freezeCounts("3 freezes", HOME_CARD))
            assertEquals("Thursday frozen", 1, frozenDays(HOME_CARD_WEEK))
        }

    @Test
    fun r3_twoMissedDays_streakHolds_twoFreezesSpent_wednesdayAndThursdayFrozen() =
        homePane(lastSolved = TODAY.minusDays(3)) {
            awaitPane(streak = "40", best = "Best 40", freezes = "2 freezes")
            assertEquals("Wednesday and Thursday frozen", 2, frozenDays(HOME_PANE_WEEK))
        }

    @Test
    fun r4_gapLongerThanHeld_streakBreaks_noFrozenDay_noFreezeCount() =
        homePane(lastSolved = LocalDate.of(2026, 11, 13)) {
            // Best 40 is the loaded run; 6 missed days outrun the 4 held, so nothing is spent and nothing is shown.
            awaitTextIn(HOME_PANE, "Best 40")
            awaitText(HOME_PANE_STREAK, "0")
            assertEquals("no freeze count at streak 0", 0, freezeCounts())
            assertEquals("no frozen day", 0, frozenDays(HOME_PANE_WEEK))
        }

    @Test
    fun r5_solveAfterMissedDay_resultNamesTheSpentFreeze_thenHomeKeepsIt() =
        shellRobot(tabAt(PlayKey), graph = graphWith(history(TODAY.minusDays(41), TODAY.minusDays(2))), darkTheme = true) {
            solveTodaysQueens()

            awaitText(RESULT_STREAK, "41 day streak")
            awaitText(RESULT_MOMENT, "Streak freeze used for Thu · 3 left")
            awaitText(RESULT_SUB, "13 more days to earn a freeze")
            assertEquals("3 freezes on Result", 1, resultFreezeCounts("3 freezes"))
            assertEquals("Thursday's ice dot", 1, resultFrozenDays())

            back()
            tapTab(HomeKey)
            awaitPane(streak = "41", best = "Best 41", freezes = "3 freezes")
            assertEquals("Thursday frozen on Home", 1, frozenDays(HOME_PANE_WEEK))
        }

    @Test
    fun r6_onlyTangoMissed_homeShowsNoFrozenDay_tangoPanelFreezesThursday() {
        val graph = graphWith(history(TODAY.minusDays(40), TODAY.minusDays(1), skip = setOf(TODAY.minusDays(1) to TANGO)))
        shellRobot(tabAt(HomeKey), graph = graph, darkTheme = true) {
            // Every other game covers Thursday, so the overall streak never spends a freeze.
            awaitPane(streak = "40", best = "Best 40", freezes = "4 freezes")
            assertEquals("no frozen day on Home", 0, frozenDays(HOME_PANE_WEEK))

            tapTab(PlayKey)
            graph.daily.addEntry(TangoPlayFixture.entry(TODAY))
            graph.daily.setCached(TangoDefinition.id, TODAY, TangoPlayFixture.envelope())
            openGame(TANGO)
            // Tango alone missed Thursday, so its own streak is one short and a freeze covers the day.
            awaitText(PLAY_STREAK, "39-day streak")
            awaitFreezeCount("3 freezes", PLAY_STATS)
            assertEquals("Thursday frozen on the Tango panel", 1, frozenDays(PLAY_WEEK))
        }
    }

    @Test
    fun r7_sevenDayRun_solveEarnsAFreeze() =
        shellRobot(tabAt(PlayKey), graph = graphWith(history(TODAY.minusDays(6), TODAY.minusDays(1))), darkTheme = true) {
            solveTodaysQueens()

            awaitText(RESULT_STREAK, "7 day streak")
            awaitText(RESULT_MOMENT, "Freeze earned · 3 held")
            awaitText(RESULT_SUB, "14 more days to earn a freeze")
            assertEquals("3 freezes on Result", 1, resultFreezeCounts("3 freezes"))
            assertEquals("no day was spent", 0, resultFrozenDays())

            back()
            tapTab(HomeKey)
            awaitPane(streak = "7", best = "Best 7", freezes = "3 freezes")
            assertEquals("no frozen day on Home", 0, frozenDays(HOME_PANE_WEEK))
        }

    private fun graphWith(records: List<CompletionRecord>) =
        E2eKoinGraph(TODAY).apply { this.records.setRecords(records) }

    /** The Home tab on a graph whose 40-day run ends [lastSolved]. */
    private fun homePane(lastSolved: LocalDate, block: ShellRobot.() -> Unit) =
        shellRobot(tabAt(HomeKey), graph = graphWith(history(lastSolved.minusDays(39), lastSolved)), darkTheme = true, block = block)

    /** Waits for the Home pane to load [streak] and [best], and the [freezes] count beside it. */
    private fun ShellRobot.awaitPane(streak: String, best: String, freezes: String) {
        awaitText(HOME_PANE_STREAK, streak)
        awaitTextIn(HOME_PANE, best)
        awaitFreezeCount(freezes, HOME_PANE)
    }

    private fun ShellRobot.solveTodaysQueens() {
        graph.daily.addEntry(DailyEntry(QueensDefinition.id, TODAY, NUMBER, "queens/n4", 0L))
        graph.daily.setCached(QueensDefinition.id, TODAY, QueensPlayFixture.envelope())
        openGame(QUEENS)
        val store = graph.koin.get<PlaySessionStore>()
        val slot = PlaySlot.Daily(QueensDefinition.id, TODAY)
        fun queens() = rule.runOnIdle { store.peek(slot) }?.controller as? QueensPlayController
        rule.waitUntil(TIMEOUT) { rule.runOnIdle { queens()?.board } != null }
        val board = checkNotNull(queens()?.board)
        rule.runOnIdle { SOLUTION.forEach { cell -> while (Cell(cell) !in board.session.queens) board.onTap(cell) } }
        awaitIdling { shows(RESULT_ROUTE) }
    }

    /** Waits until the node tagged [tag] reads exactly [text]: the history's flow and the count-up settle after a route shows. */
    private fun ShellRobot.awaitText(tag: String, text: String) {
        rule.waitUntil(TIMEOUT) {
            rule.onAllNodes(hasTestTag(tag) and hasTextExactly(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun ShellRobot.awaitTextIn(tag: String, text: String) {
        rule.waitUntil(TIMEOUT) {
            rule.onAllNodes(hasText(text) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun ShellRobot.awaitFreezeCount(label: String, within: String) {
        rule.waitUntil(TIMEOUT) { freezeCounts(label, within) > 0 }
    }

    /** Freeze counts on screen reading [label], or any count when null; only those under the node tagged [within] when given. */
    private fun ShellRobot.freezeCounts(label: String? = null, within: String? = null): Int {
        val count = if (label == null) isFreezeCount else hasContentDescription(label)
        val scoped = if (within == null) count else count and hasAnyAncestor(hasTestTag(within))
        return rule.onAllNodes(scoped, useUnmergedTree = true).fetchSemanticsNodes().size
    }

    private fun ShellRobot.resultFreezeCounts(label: String): Int =
        rule.onAllNodes(hasTestTag(RESULT_FREEZES) and hasContentDescription(label), useUnmergedTree = true).fetchSemanticsNodes().size

    /** Days of the week strip tagged [week] that a freeze covers (a snowflake in place of the number). */
    private fun ShellRobot.frozenDays(week: String): Int =
        rule.onAllNodes(hasContentDescription("Frozen") and hasAnyAncestor(hasTestTag(week)), useUnmergedTree = true).fetchSemanticsNodes().size

    private fun ShellRobot.resultFrozenDays(): Int = rule.onAllNodes(isFrozenResultDay, useUnmergedTree = true).fetchSemanticsNodes().size

    /** Every daily game solved each day in [from]..[to], except the (day, game) pairs in [skip]. */
    private fun history(from: LocalDate, to: LocalDate, skip: Set<Pair<LocalDate, String>> = emptySet()): List<CompletionRecord> {
        val out = mutableListOf<CompletionRecord>()
        var day = from
        while (!day.isAfter(to)) {
            val n = day.toEpochDay().toInt()
            val refs = listOf(
                ZIP to ZipPuzzleRef(PuzzleKey.Daily(day, n)),
                TANGO to TangoPuzzleRef(PuzzleKey.Daily(day, n)),
                QUEENS to QueensPuzzleRef(PuzzleKey.Daily(day, n)),
                "mini" to SudokuPuzzleRef(SudokuGames.MINI.id, PuzzleKey.Daily(day, n)),
                "classic" to SudokuPuzzleRef(SudokuGames.CLASSIC.id, PuzzleKey.Daily(day, null)),
                "strands" to StrandsPuzzleRef(PuzzleKey.Daily(day, StrandsNumber.of(day))),
            )
            for ((game, ref) in refs) if (day to game !in skip) out += record(ref)
            day = day.plusDays(1)
        }
        return out
    }

    private fun record(ref: PuzzleRef) = CompletionRecord(ref, 90_000L, 0, RecordStats(1, "{}"))

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 11, 20)
        const val QUEENS = "queens"
        const val TANGO = "tango"
        const val ZIP = "zip"
        const val NUMBER = 7
        const val TIMEOUT = 5_000L
        const val RESULT_ROUTE = "route.result"
        const val RESULT_STREAK = "result.side.streak"
        const val RESULT_SUB = "result.side.streak.sub"
        const val RESULT_MOMENT = "result.side.streak.moment"
        const val RESULT_FREEZES = "result.side.streak.freezes"
        const val PLAY_STATS = "play.stats"
        const val PLAY_STREAK = "play.stats.streak"
        const val PLAY_WEEK = "play.stats.week"
        const val HOME_PANE = "home.streak_pane"
        const val HOME_PANE_WEEK = "home.pane_week"
        const val HOME_PANE_STREAK = "home.pane_streak"
        const val HOME_CARD = "home.streak"
        const val HOME_CARD_WEEK = "home.week"

        /** A solution of the 4×4 fixture (rows are its regions); the fixture's own listed one has touching queens. */
        val SOLUTION = listOf(1, 7, 8, 14)

        val FREEZE_COUNT = Regex("""\d+ freezes?""")
        val isFreezeCount = SemanticsMatcher("freeze count") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { FREEZE_COUNT.matches(it) } == true
        }
        val isFrozenResultDay = SemanticsMatcher("frozen result week day") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("result.side.week.") == true &&
                node.config.getOrNull(SemanticsProperties.StateDescription) == "Frozen"
        }
    }
}
