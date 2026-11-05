package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.test.onAllNodesWithText
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.play.ZipPlayFixture
import com.ivandsky.puzzlebox.data.di.dataModule
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipSession
import com.ivandsky.puzzlebox.games.zipui.ZipPlayController
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Persistence rows over the real shell: N19 / A24 (a kill with no saved state resumes the
 * same board, elapsed time and undo from a file-backed Room database), N20 / SC9 (a Result
 * restored after process death renders and its actions work) and SC8 (a solve deletes the
 * save, the cap holds at 8, a pinned clock across midnight keeps the live board's day while
 * the cues move on).
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = android.app.Application::class)
class ProcessDeathE2ETest {

    /** Every "process" Room was opened in; each relaunch opens the same database file anew. */
    private val processes = mutableListOf<KoinApplication>()

    @After
    fun closeProcesses() {
        processes.forEach(KoinApplication::close)
    }

    /**
     * The E2E graph with the production Room [SavedPlayRepository] in place of the fake: each
     * graph start resolves it from a fresh data graph, i.e. a new Room instance over the
     * app's database file, so only what reached the disk survives a [ShellRobot.relaunch].
     */
    private fun roomGraph() = E2eKoinGraph(
        E2E_TODAY,
        overrides = module {
            single<SavedPlayRepository> {
                koinApplication {
                    androidContext(RuntimeEnvironment.getApplication())
                    modules(dataModule)
                }.also(processes::add).koin.get()
            }
        },
    )

    @Test
    fun `N19 a kill without saved state resumes the same board, time and undo`() = shellRobot(playHub, graph = roomGraph()) {
        openGame(ZIP)
        awaitZip()
        rule.runOnIdle { MID_GAME.forEach { zip().extend(it) } }

        background()
        val killed = checkNotNull(zip().session)
        // The flush is launched on stop; Room commits on its own executor, so wait for the row.
        rule.waitUntil(5_000) { runBlocking { saves.load(todaySlot) } != null }
        foreground()

        relaunch()
        assertState(playHub)
        openGame(ZIP)
        val resumed = awaitZip()

        assertEquals("same board", killed.path, resumed.path)
        assertEquals("same undo stack", killed.history, resumed.history)
        assertEquals("same elapsed time (±1 s)", killed.elapsedMs.toDouble(), resumed.elapsedMs.toDouble(), 1_000.0)
        rule.runOnIdle { zip().undo() }
        assertEquals("undo works after the kill", killed.path.dropLast(1), rule.runOnIdle { zip().session?.path })
    }

    @Test
    fun `N20 a Result restored after process death renders, and Play again reopens its day`() =
        shellRobot(pastResult, graph = replayableGraph()) {
            assertRoute(RESULT)
            awaitText("Solved!")

            tap(PLAY_AGAIN)

            assertState(stacks(tab = HomeKey, home = listOf(HomeKey, HistoryKey, GameKey(ZIP, "$PAST_DAY"))))
            assertRoute(GAME)
        }

    @Test
    fun `N20 a Result restored after process death returns to its origin on Done`() =
        shellRobot(pastResult, graph = replayableGraph()) {
            assertRoute(RESULT)

            tap(DONE)

            assertState(stacks(tab = HomeKey, home = listOf(HomeKey, HistoryKey)))
            assertRoute(HISTORY)
        }

    @Test
    fun `N20 today's Result restored after process death opens the next game on Next`() {
        val todayResult = stacks(tab = PlayKey, play = listOf(PlayKey, ResultKey(ZIP, "$E2E_TODAY", TIME_MS, 0)))
        shellRobot(todayResult) {
            assertRoute(RESULT)
            awaitText("Solved!")

            tap(NEXT)

            val next = state.currentStack.last()
            assertTrue("Next opens another game's today: $state", next is GameKey && next.gameId != ZIP && next.day == null)
            assertState(stacks(tab = PlayKey, play = listOf(PlayKey, next)))
            assertRoute(GAME)
        }
    }

    @Test
    fun `SC8 solving deletes the save`() = shellRobot(playHub, graph = roomGraph()) {
        openGame(ZIP)
        awaitZip()
        rule.runOnIdle { MID_GAME.forEach { zip().extend(it) } }
        background()
        assertNotNull("mid-game is saved", runBlocking { saves.load(todaySlot) })
        foreground()

        rule.runOnIdle { ZipPlayFixture.solution.drop(MID_GAME.size).forEach { zip().extend(it) } }
        assertRoute(RESULT)

        rule.waitUntil(TIMEOUT_MS) { runBlocking { saves.load(todaySlot) } == null }
    }

    @Test
    fun `SC8 the newest save evicts the oldest past the cap of 8`() = shellRobot(playHub, graph = roomGraph()) {
        val older = (CAP downTo 1).map { PlaySlot(ZipDefinition.id, E2E_TODAY.minusDays(it.toLong())) }
        runBlocking {
            older.forEach { slot ->
                saves.save(slot, byteArrayOf(1))
                // Room stamps saves in epoch ms: keep the seeded order strict.
                Thread.sleep(SAVE_GAP_MS)
            }
        }

        openGame(ZIP)
        awaitZip()
        rule.runOnIdle { MID_GAME.forEach { zip().extend(it) } }
        background()

        val kept = runBlocking { saves.observeInProgress().first() }
        assertEquals("the cap holds", CAP, kept.size)
        assertTrue("the newest save is kept: $kept", todaySlot in kept)
        assertTrue("the oldest save is evicted: $kept", older.first() !in kept)
        foreground()
    }

    @Test
    fun `SC8 across midnight the live board keeps its day and the cues move on`() = shellRobot(playHub) {
        openGame(ZIP)
        awaitZip()
        rule.runOnIdle { MID_GAME.forEach { zip().extend(it) } }
        background()
        foreground()
        tapTab(HomeKey)
        awaitText("Resume")

        graph.calendar.setAll(E2E_TODAY.plusDays(1))

        awaitText("Play today")
        assertTrue("no resume cue for yesterday's save", rule.onAllNodesWithText("Resume").fetchSemanticsNodes().isEmpty())
        tapTab(PlayKey)
        assertState(stacks(tab = PlayKey, play = listOf(PlayKey, GameKey(ZIP))))
        assertRoute(GAME)
        assertEquals("the live board keeps its day and trace", MID_GAME, rule.runOnIdle { zip().session?.path })
        assertNull("no board opened for the new day", rule.runOnIdle { store.peek(PlaySlot(ZipDefinition.id, E2E_TODAY.plusDays(1))) })
    }

    private val ShellRobot.store: PlaySessionStore get() = graph.koin.get()
    private val ShellRobot.saves: SavedPlayRepository get() = graph.koin.get()

    /** The live play of [todaySlot] (read off the main thread only while the activity is stopped). */
    private fun ShellRobot.zip(): ZipPlayController =
        checkNotNull(store.peek(todaySlot)) { "no live zip play for $E2E_TODAY" }.controller as ZipPlayController

    /** Waits until today's live Zip has its session, and returns it. */
    private fun ShellRobot.awaitZip(): ZipSession {
        rule.waitUntil(TIMEOUT_MS) { rule.runOnIdle { store.peek(todaySlot) }?.let { (it.controller as ZipPlayController).session } != null }
        return checkNotNull(rule.runOnIdle { zip().session })
    }

    private fun ShellRobot.awaitText(text: String) {
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ZipPlayController.extend(cell: Cell) = onDragEvent(DragEvent.Extend(GridCell(cell.idx / 5, cell.idx % 5)))

    /** [PAST_DAY] is a captured, cached day, so its Result's "Play again" has a board to open. */
    private fun replayableGraph() = E2eKoinGraph(E2E_TODAY).apply {
        daily.setHistory(ZipDefinition.id, listOf(ZipPlayFixture.entry(E2E_TODAY), ZipPlayFixture.entry(PAST_DAY)))
        daily.setCached(ZipDefinition.id, PAST_DAY, ZipPlayFixture.envelope())
    }

    private fun stacks(
        tab: NavKey,
        home: List<NavKey> = listOf(HomeKey),
        play: List<NavKey> = listOf(PlayKey),
        profile: List<NavKey> = listOf(ProfileKey),
    ) = NavigationState(tab = tab, stacks = listOf(home, play, profile))

    private val playHub = stacks(tab = PlayKey)

    /** A History replay's Result as the restored navigation state hands it to a fresh process. */
    private val pastResult = stacks(tab = HomeKey, home = listOf(HomeKey, HistoryKey, ResultKey(ZIP, "$PAST_DAY", TIME_MS, 0)))

    private val todaySlot = PlaySlot(ZipDefinition.id, E2E_TODAY)

    private companion object {
        const val HISTORY = "route.history"
        const val GAME = "route.game"
        const val RESULT = "route.result"
        const val PLAY_AGAIN = "result.playAgain"
        const val NEXT = "result.next"
        const val DONE = "result.done"
        const val ZIP = "zip"
        const val CAP = 8
        const val TIME_MS = 12_340L
        const val SAVE_GAP_MS = 5L
        const val TIMEOUT_MS = 5_000L
        val PAST_DAY: LocalDate = LocalDate.of(2026, 10, 30)
        val MID_GAME: List<Cell> = ZipPlayFixture.solution.take(3)
    }
}
