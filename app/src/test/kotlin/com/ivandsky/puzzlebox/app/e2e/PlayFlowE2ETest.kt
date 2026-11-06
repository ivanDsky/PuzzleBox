package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.play.ZipPlayFixture
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.games.queens.QueensPuzzleRef
import com.ivandsky.puzzlebox.games.strands.StrandsNumber
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzleRef
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzleRef
import com.ivandsky.puzzlebox.games.tango.TangoPuzzleRef
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipSession
import com.ivandsky.puzzlebox.games.zipui.ZipPlayController
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Play-flow rows of the transition table (spec §2.6) over the real shell: N2, N4–N9 (N3 is
 * PlayBackE2ETest's), SC4 (back from Result lands on its origin — the hub here in N5, Home's
 * "Play today" in N9; the History replay origin is HistoryE2ETest's N11) and SC10 ("Next"
 * names the next unsolved daily; all done shows when nothing is left, and Done returns to
 * the origin). Every row asserts the navigation state (with SC2's selected tab) and the
 * visible `route.*` tag.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = android.app.Application::class)
class PlayFlowE2ETest {

    @Test
    fun `N2 picking Zip on the hub pushes its game`() = shellRobot(playHub) {
        assertRoute(HUB)

        openGame(ZIP)

        assertState(inZip)
        assertRoute(GAME)
    }

    @Test
    fun `N4 solving replaces the game with today's result`() = shellRobot(playHub) {
        solveZipFromHub()

        assertState(stacks(tab = PlayKey, play = listOf(PlayKey, result())))
        assertRoute(RESULT)
    }

    @Test
    fun `N5 SC4 back from the result returns to the hub`() = shellRobot(playHub) {
        solveZipFromHub()

        back()

        assertState(playHub)
        assertRoute(HUB)
    }

    @Test
    fun `N5 SC10 Up when all is solved returns to the hub`() = shellRobot(playHub, graph = solvedOthersGraph()) {
        solveZipFromHub()
        awaitTag(ALL_DONE)

        tap(UP)

        assertState(playHub)
        assertRoute(HUB)
    }

    @Test
    fun `N6 Play again replaces the result with a fresh board of the same day`() = shellRobot(playHub) {
        solveZipFromHub()

        // Result scrolls on the 320×470 test window; Play again sits below the fold.
        awaitTag(PLAY_AGAIN)
        rule.onNodeWithTag(PLAY_AGAIN).performScrollTo().performClick()
        rule.waitForIdle()

        assertState(stacks(tab = PlayKey, play = listOf(PlayKey, GameKey(ZIP, "$E2E_TODAY"))))
        assertRoute(GAME)
        rule.waitUntil(TIMEOUT_MS) { rule.runOnIdle { zip().session?.let { !it.isComplete } } == true }
        assertEquals("fresh board", emptyList<Cell>(), rule.runOnIdle { zip().session?.path })
    }

    @Test
    fun `N7 SC10 Next names Tango and replaces the result with its game`() = shellRobot(playHub) {
        solveZipFromHub()
        awaitTag(NEXT)
        rule.onNodeWithTag(NEXT).assertTextEquals("Next: Tango")

        tap(NEXT)

        assertState(stacks(tab = PlayKey, play = listOf(PlayKey, GameKey(TANGO))))
        assertRoute(GAME)
    }

    @Test
    fun `SC10 Next skips a solved daily`() = shellRobot(playHub, graph = solvedGraph(tangoRecord())) {
        solveZipFromHub()
        awaitTag(NEXT)
        rule.onNodeWithTag(NEXT).assertTextEquals("Next: Queens")

        tap(NEXT)

        assertState(stacks(tab = PlayKey, play = listOf(PlayKey, GameKey(QUEENS))))
        assertRoute(GAME)
    }

    @Test
    fun `SC10 all solved shows the countdown instead of Next`() = shellRobot(playHub, graph = solvedOthersGraph()) {
        solveZipFromHub()

        awaitTag(ALL_DONE)
        awaitTag(COUNTDOWN)
        rule.onNodeWithTag(NEXT).assertDoesNotExist()
        assertRoute(RESULT)
    }

    @Test
    fun `N8 Play today on Home pushes the featured game onto Home`() = shellRobot {
        assertRoute(HOME)

        tap(HOME_PLAY)

        assertState(stacks(tab = HomeKey, home = listOf(HomeKey, GameKey(ZIP))))
        assertRoute(GAME)
    }

    @Test
    fun `N9 SC4 solving from Home, then back, returns to Home`() = shellRobot {
        tap(HOME_PLAY)
        awaitZip()

        solveZip()
        assertState(stacks(tab = HomeKey, home = listOf(HomeKey, result())))
        assertRoute(RESULT)

        back()
        assertState(stacks(tab = HomeKey))
        assertRoute(HOME)
    }

    @Test
    fun `SC10 Up when all is solved from Home returns to Home`() = shellRobot(graph = solvedOthersGraph()) {
        tap(HOME_PLAY)
        awaitZip()
        solveZip()
        awaitTag(ALL_DONE)

        tap(UP)

        assertState(stacks(tab = HomeKey))
        assertRoute(HOME)
    }

    private fun ShellRobot.solveZipFromHub() {
        openGame(ZIP)
        awaitZip()
        solveZip()
    }

    private fun ShellRobot.solveZip() {
        rule.runOnIdle { ZipPlayFixture.solution.forEach { zip().extend(it) } }
        awaitTag(RESULT)
    }

    /** The result the solve pushed; its time and hints come from the live session. */
    private fun ShellRobot.result(): ResultKey {
        val top = state.currentStack.last()
        assertTrue("today's Zip result on top: $state", top is ResultKey && top.gameId == ZIP && top.day == "$E2E_TODAY")
        return top as ResultKey
    }

    private val ShellRobot.store: PlaySessionStore get() = graph.koin.get()

    private fun ShellRobot.zip(): ZipPlayController =
        checkNotNull(store.peek(todaySlot)) { "no live zip play for $E2E_TODAY" }.controller as ZipPlayController

    private fun ShellRobot.awaitZip(): ZipSession {
        rule.waitUntil(TIMEOUT_MS) { rule.runOnIdle { store.peek(todaySlot) }?.let { (it.controller as ZipPlayController).session } != null }
        return checkNotNull(rule.runOnIdle { zip().session })
    }

    private fun ZipPlayController.extend(cell: Cell) = onDragEvent(DragEvent.Extend(GridCell(cell.idx / 5, cell.idx % 5)))

    /** Every other game is solved today, so a Zip solve leaves nothing next. */
    private fun solvedOthersGraph() = solvedGraph(
        tangoRecord(),
        record(QueensPuzzleRef(E2E_TODAY, 1)),
        record(SudokuPuzzleRef(SudokuGames.MINI.id, E2E_TODAY, 1)),
        record(SudokuPuzzleRef(SudokuGames.CLASSIC.id, E2E_TODAY, null)),
        record(StrandsPuzzleRef(E2E_TODAY, StrandsNumber.of(E2E_TODAY))),
    )

    private fun solvedGraph(vararg records: CompletionRecord) = E2eKoinGraph(E2E_TODAY).apply {
        this.records.setRecords(records.toList())
    }

    private fun tangoRecord() = record(TangoPuzzleRef(E2E_TODAY, 301))

    private fun record(ref: PuzzleRef) = CompletionRecord(
        puzzleRef = ref,
        time = 60_000,
        hints = 0,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
    )

    private fun stacks(
        tab: NavKey,
        home: List<NavKey> = listOf(HomeKey),
        play: List<NavKey> = listOf(PlayKey),
        profile: List<NavKey> = listOf(ProfileKey),
    ) = NavigationState(tab = tab, stacks = listOf(home, play, profile))

    private val playHub = stacks(tab = PlayKey)
    private val inZip = stacks(tab = PlayKey, play = listOf(PlayKey, GameKey(ZIP)))
    private val todaySlot = PlaySlot(ZipDefinition.id, E2E_TODAY)

    private companion object {
        const val HOME = "route.home"
        const val HUB = "route.hub"
        const val GAME = "route.game"
        const val RESULT = "route.result"
        const val NEXT = "result.next"
        const val UP = "result.up"
        const val PLAY_AGAIN = "result.playAgain"
        const val ALL_DONE = "result.all_done"
        const val COUNTDOWN = "result.countdown"
        const val HOME_PLAY = "home.today_play"
        const val ZIP = "zip"
        const val TANGO = "tango"
        const val QUEENS = "queens"
        const val TIMEOUT_MS = 5_000L
    }
}
