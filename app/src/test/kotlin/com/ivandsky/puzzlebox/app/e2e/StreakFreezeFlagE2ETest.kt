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
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensPuzzleRef
import com.ivandsky.puzzlebox.games.queensui.QueensPlayController
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.PlayKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The streak freeze over the real app and the real Queens, on a tablet where Today's streak pane, the play
 * side panel and the Result all show. The history misses yesterday: off, every surface keeps the strict streak and
 * hides every freeze element; on, the missed day spends a held freeze, so the run holds and the surfaces show the
 * count, the frozen day and, on Result, the spent-freeze moment. The play panel sits only on the board, so it is read
 * before the solve; the other surfaces after it.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w1280dp-h800dp-land")
class StreakFreezeFlagE2ETest {

    @Test
    fun flagOff_streakSurfacesUnchanged() =
        shellRobot(tabAt(PlayKey), graph = historyWithMissedYesterday()) {
            openTodaysQueens()
            // Strict: yesterday was missed and today is open, so no run stands yet.
            awaitText(PLAY_STREAK, "No streak yet — solve today to start one")
            assertEquals("no freeze count on the play panel", 0, freezeCounts())
            assertEquals("no frozen day on the play panel", 0, frozenDays(PLAY_WEEK))

            solveTodaysQueens()

            awaitText(RESULT_STREAK, "1 day streak")
            assertEquals("no freeze count on Result", 0, freezeCounts())
            assertEquals("no frozen day on Result", 0, resultFrozenDays())
            assertFalse("no freeze moment on Result", shows(RESULT_MOMENT))

            back()
            tapTab(HomeKey)

            awaitText(HOME_PANE_STREAK, "1")
            awaitTextIn(HOME_PANE_TAG, "Best 2")
            assertEquals("no freeze count on Home", 0, freezeCounts())
            assertEquals("no frozen day in the Home week", 0, frozenDays(HOME_PANE_WEEK))
        }

    @Test
    fun flagOn_missedDaySpendsFreeze() =
        shellRobot(tabAt(PlayKey), graph = historyWithMissedYesterday(ReleaseFlag.StreakFreeze)) {
            openTodaysQueens()
            // Opening today finds yesterday missed with a freeze held: it is already spent.
            awaitText(PLAY_STREAK, "2-day streak")
            assertEquals("one freeze held on the play panel", 1, freezeCounts("1 freeze"))
            assertEquals("yesterday frozen on the play panel", 1, frozenDays(PLAY_WEEK))

            solveTodaysQueens()

            awaitText(RESULT_STREAK, "3 day streak")
            awaitText(RESULT_MOMENT, "Streak freeze used for Tue · 1 left")
            assertEquals("one freeze left on Result", 1, freezeCounts("1 freeze"))
            assertEquals("yesterday frozen on Result", 1, resultFrozenDays())

            back()
            tapTab(HomeKey)

            awaitText(HOME_PANE_STREAK, "3")
            awaitTextIn(HOME_PANE_TAG, "Best 3")
            assertEquals("one freeze held in Home", 1, freezeCounts("1 freeze", HOME_PANE_TAG))
            assertEquals("yesterday frozen in the Home week", 1, frozenDays(HOME_PANE_WEEK))
        }

    /** Queens solved on the 1st and 2nd of the month, today the 4th: the 3rd is the missed day. */
    private fun historyWithMissedYesterday(vararg flags: ReleaseFlag) = shellGraph(*flags).apply {
        val earlier = listOf(E2E_TODAY.minusDays(3) to 4, E2E_TODAY.minusDays(2) to 5)
        records.setRecords(
            earlier.map { (day, number) ->
                CompletionRecord(QueensPuzzleRef(PuzzleKey.Daily(day, number)), TIME_MS, 0, RecordStats(1, "{}"))
            },
        )
    }

    private fun ShellRobot.openTodaysQueens() {
        graph.daily.addEntry(DailyEntry(QueensDefinition.id, E2E_TODAY, NUMBER, "queens/n4", 0L))
        // The real repository keeps today's puzzle in its cache; the fake serves it only from here.
        graph.daily.setCached(QueensDefinition.id, E2E_TODAY, QueensPlayFixture.envelope())
        openGame(QUEENS)
    }

    /** Solves today's open Queens through its real controller and waits for the Result. */
    private fun ShellRobot.solveTodaysQueens() {
        val store = graph.koin.get<PlaySessionStore>()
        val slot = PlaySlot.Daily(QueensDefinition.id, E2E_TODAY)
        fun queens() = rule.runOnIdle { store.peek(slot) }?.controller as? QueensPlayController
        rule.waitUntil(TIMEOUT) { rule.runOnIdle { queens()?.board } != null }
        val board = checkNotNull(queens()?.board)
        // A cell cycles blank, X, queen; an auto-cross may skip the X, so tap until the queen stands.
        rule.runOnIdle { SOLUTION.forEach { cell -> while (Cell(cell) !in board.session.queens) board.onTap(cell) } }
        // The win's handoff runs on the main looper, which a frame-only wait never idles.
        awaitIdling { shows(RESULT_ROUTE) }
    }

    /** Waits until the node tagged [tag] reads exactly [text], since a count-up and the history's flow settle after the route shows. */
    private fun ShellRobot.awaitText(tag: String, text: String) {
        rule.waitUntil(TIMEOUT) {
            rule.onAllNodes(hasTestTag(tag) and hasTextExactly(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Waits until a text node reads [text] under the node tagged [tag]. */
    private fun ShellRobot.awaitTextIn(tag: String, text: String) {
        rule.waitUntil(TIMEOUT) {
            rule.onAllNodes(hasText(text) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Freeze counts on screen reading [label], or any count when null; only those under the node tagged [within] when given. */
    private fun ShellRobot.freezeCounts(label: String? = null, within: String? = null): Int {
        val count = if (label == null) isFreezeCount else hasContentDescription(label)
        val scoped = if (within == null) count else count and hasAnyAncestor(hasTestTag(within))
        return rule.onAllNodes(scoped, useUnmergedTree = true).fetchSemanticsNodes().size
    }

    /** Days of the week strip tagged [week] that a freeze covers (a snowflake in place of the number). */
    private fun ShellRobot.frozenDays(week: String): Int =
        rule.onAllNodes(hasContentDescription("Frozen") and hasAnyAncestor(hasTestTag(week)), useUnmergedTree = true).fetchSemanticsNodes().size

    private fun ShellRobot.resultFrozenDays(): Int =
        rule.onAllNodes(isFrozenResultDay, useUnmergedTree = true).fetchSemanticsNodes().size

    private companion object {
        const val QUEENS = "queens"
        const val NUMBER = 7
        const val TIMEOUT = 5_000L
        const val TIME_MS = 90_000L
        const val RESULT_ROUTE = "route.result"
        const val RESULT_STREAK = "result.side.streak"
        const val RESULT_MOMENT = "result.side.streak.moment"
        const val PLAY_STREAK = "play.stats.streak"
        const val PLAY_WEEK = "play.stats.week"
        const val HOME_PANE_TAG = "home.streak_pane"
        const val HOME_PANE_WEEK = "home.pane_week"
        const val HOME_PANE_STREAK = "home.pane_streak"

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
