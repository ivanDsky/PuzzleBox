package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.play.QueensPlayFixture
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensPuzzleRef
import com.ivandsky.puzzlebox.games.queensui.QueensPlayController
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.gameui.replay.ReplayCopy
import com.ivandsky.puzzlebox.gameui.replay.ReplayTags
import com.ivandsky.puzzlebox.model.SolveReplay
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ReplayCompareKey
import com.ivandsky.puzzlebox.play.api.ReplayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The walking skeleton of the full replay over the real app and the real Queens: the flag decides whether a solve
 * stores a replay and whether the own result offers it. On, the result shows Watch replay, the row opens the replay
 * page and Next move steps the playback; off, no replay is stored and the result shows no row.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class FullReplayFlagE2ETest {

    @Test
    fun withTheFlagOnTheResultOpensTheReplayAndNextMoveStepsIt() =
        shellRobot(tabAt(PlayKey), graph = shellGraph(ReleaseFlag.FullReplay)) {
            solveTodaysQueens()
            awaitActionable(WATCH_ROW)

            tap(WATCH_ROW)

            assertRoute(REPLAY_ROUTE)
            awaitTag(ReplayTags.NEXT)
            val end = seek().range.endInclusive
            // The page opens paused on the finished board, so step back to the start before stepping forward.
            repeat(MOVE_BUDGET) { tap(ReplayTags.PREVIOUS) }
            assertEquals("stepping back reaches the start", 0f, seek().current, 0f)
            tap(ReplayTags.NEXT)
            assertTrue("Next move steps to the first move", seek().current > 0f)
            repeat(MOVE_BUDGET) { tap(ReplayTags.NEXT) }
            assertEquals("stepping on reaches the solve's end", end, seek().current, 0f)

            back()
            assertRoute("route.result")
        }

    @Test
    fun withTheFlagOffTheSolveStoresNoReplayAndTheResultOffersNone() =
        shellRobot(tabAt(PlayKey), graph = shellGraph()) {
            solveTodaysQueens()
            rule.waitForIdle()

            assertFalse("no Watch replay row", shows(WATCH_ROW))
            assertFalse("no unavailable row either", shows(ReplayTags.UNAVAILABLE))
            val ref = QueensPuzzleRef(PuzzleKey.Daily(E2E_TODAY, NUMBER))
            assertEquals("the completion itself is stored", 1, runBlocking { graph.records.observeRecords().first() }.size)
            assertNull("no replay is stored", runBlocking { graph.records.latestReplay(ref) })
        }

    @Test
    fun withTheFlagOffAStoredReplayIsOfferedNowhere() {
        val replay = queensReplay()
        shellRobot(tabAt(PlayKey, STORED_RESULT), graph = storedSolve(replay)) {
            awaitTag(RESULT_ROUTE)
            rule.waitForIdle()
            assertFalse("no Watch replay row", shows(WATCH_ROW))
            assertFalse("no unavailable row", shows(ReplayTags.UNAVAILABLE))

            push(ReplayKey.daily(QUEENS, "$E2E_TODAY", NUMBER, latest = true))
            awaitTag(ReplayTags.NONE)
            assertFalse("no player", shows(ReplayTags.PLAY))

            push(withoutTheirSide())
            awaitTag(COMPARE_ROUTE)
            awaitTag(ReplayTags.NONE)
            assertFalse("no compare player", shows(ReplayTags.PLAY))
        }
    }

    @Test
    fun withTheFlagOnAStoredSolveWithoutAReplayOffersADisabledWatchReplayRow() =
        shellRobot(tabAt(PlayKey, STORED_RESULT), graph = storedSolve(replay = null, ReleaseFlag.FullReplay)) {
            awaitTag(ReplayTags.UNAVAILABLE)

            rule.onNodeWithTag(ReplayTags.UNAVAILABLE).assertIsNotEnabled().assertHasNoClickAction()
            rule.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag(ReplayTags.UNAVAILABLE))).assertCountEquals(0)
            rule.onNodeWithText(ReplayCopy.WATCH).assertExists()
            rule.onNodeWithText(ReplayCopy.UNAVAILABLE).assertExists()
            assertFalse("no live Watch replay row", shows(WATCH_ROW))
        }

    @Test
    fun withTheFlagOnAStoredReplayThatTheResultOpensHasNamedControls() {
        val replay = queensReplay()
        shellRobot(tabAt(PlayKey, STORED_RESULT), graph = storedSolve(replay, ReleaseFlag.FullReplay)) {
            awaitActionable(WATCH_ROW)
            tap(WATCH_ROW)
            awaitTag(ReplayTags.NEXT)

            rule.onNodeWithTag(ReplayTags.PLAY).assertContentDescriptionEquals(ReplayCopy.PLAY)
            rule.onNodeWithTag(ReplayTags.PREVIOUS).assertContentDescriptionEquals(ReplayCopy.PREVIOUS_MOVE)
            rule.onNodeWithTag(ReplayTags.NEXT).assertContentDescriptionEquals(ReplayCopy.NEXT_MOVE)
            rule.onNodeWithTag(ReplayTags.SPEED).assertContentDescriptionEquals(ReplayCopy.speedDescription(1))
            rule.onNodeWithTag(ReplayTags.SHOW_ERRORS).assertContentDescriptionEquals(ReplayCopy.SHOW_ERRORS)
            rule.onNodeWithTag(ReplayTags.SKIP_IDLE).assertExists()
        }
    }

    @Test
    fun aCompareWhoseOtherSideHasNoReplaySaysNoReplayThereAndStillPlaysMine() {
        val replay = queensReplay()
        shellRobot(tabAt(PlayKey), graph = storedSolve(replay, ReleaseFlag.FullReplay)) {
            push(withoutTheirSide())
            awaitTag(COMPARE_ROUTE)
            awaitTag(ReplayTags.PLAY)

            awaitIdling { rule.onAllNodesWithText(ReplayCopy.NO_REPLAY).fetchSemanticsNodes().isNotEmpty() }
            assertFalse("my side is not the empty page", shows(ReplayTags.NONE))
        }
    }

    /** A real Queens solve's replay, recorded in a flag-on app: the bytes a later, flag-off app finds stored. */
    private fun queensReplay(): SolveReplay {
        var replay: SolveReplay? = null
        shellRobot(tabAt(PlayKey), graph = shellGraph(ReleaseFlag.FullReplay)) {
            solveTodaysQueens()
            replay = runBlocking { graph.records.latestReplay(QUEENS_REF) }
        }
        return checkNotNull(replay) { "the flag-on solve stored a replay" }
    }

    /** Today's Queens, solved in [STORED_RESULT]'s terms with [replay] stored beside it, under [flags]. */
    private fun storedSolve(replay: SolveReplay?, vararg flags: ReleaseFlag) = shellGraph(*flags).apply {
        daily.addEntry(DailyEntry(QueensDefinition.id, E2E_TODAY, NUMBER, "queens/n4", 0L))
        daily.setCached(QueensDefinition.id, E2E_TODAY, QueensPlayFixture.envelope())
        runBlocking {
            records.onCompleted(
                CompletionRecord(QUEENS_REF, STORED_TIME_MS, 0, RecordStats(schemaVersion = 1, blob = "{}"), isRecord = true),
                replay,
            )
        }
    }

    /** My latest solve against a theirs-leg that names no duel, so the other side has no replay. */
    private fun withoutTheirSide() = ReplayCompareKey.of(
        mine = ReplayKey.daily(QUEENS, "$E2E_TODAY", NUMBER, latest = true),
        theirs = ReplayKey.daily(QUEENS, "$E2E_TODAY", NUMBER, latest = false),
        myName = "You",
        theirName = "Maya",
    ).copy(theirsKind = ReplayKey.THEIR_LEG)

    /** Solves today's Queens through its real controller, the history row giving the result its puzzle number. */
    private fun ShellRobot.solveTodaysQueens() {
        graph.daily.addEntry(DailyEntry(QueensDefinition.id, E2E_TODAY, NUMBER, "queens/n4", 0L))
        // The real repository keeps today's puzzle in its cache; the fake serves it only from here.
        graph.daily.setCached(QueensDefinition.id, E2E_TODAY, QueensPlayFixture.envelope())
        openGame(QUEENS)
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

    private fun ShellRobot.seek(): ProgressBarRangeInfo =
        rule.onNodeWithTag(ReplayTags.SEEK).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]

    private companion object {
        const val QUEENS = "queens"
        const val NUMBER = 7
        const val TIMEOUT = 5_000L
        const val RESULT_ROUTE = "route.result"
        const val COMPARE_ROUTE = "route.replay-compare"
        const val STORED_TIME_MS = 61_000L
        val STORED_RESULT = ResultKey(QUEENS, "$E2E_TODAY", STORED_TIME_MS, 0)
        val QUEENS_REF = QueensPuzzleRef(PuzzleKey.Daily(E2E_TODAY, NUMBER))
        const val REPLAY_ROUTE = "route.replay"
        const val WATCH_ROW = ReplayTags.WATCH_ROW
        /** A solution of the 4×4 fixture (rows are its regions); the fixture's own listed one has touching queens. */
        val SOLUTION = listOf(1, 7, 8, 14)

        /** More than the solve's moves: a cross and a queen per cell. */
        const val MOVE_BUDGET = 3 * 4
    }
}
