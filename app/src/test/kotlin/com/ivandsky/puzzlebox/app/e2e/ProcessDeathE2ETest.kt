package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.play.ZipPlayFixture
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.data.di.dataModule
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipSession
import com.ivandsky.puzzlebox.games.zipui.ZipPlayController
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
 * same board, elapsed time and undo from a file-backed Room database), N20 / SC9 (a solve's
 * Result, saved and restored into a fresh process, renders and its actions work) and SC8 (a
 * solve deletes the save, the cap holds at 8, a pinned clock across midnight keeps the live
 * board's day, labelled yesterday's, while the cues move on).
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
        awaitSaved()
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
    fun `N20 a Result saved from Play renders after process death, and Play again reopens its day`() = shellRobot(playHub) {
        solveThenDie(todaySlot)
        awaitTag(RESULT_SCREEN)

        rule.onNodeWithTag(PLAY_AGAIN).performScrollTo().performClick()
        rule.waitForIdle()

        assertState(stacks(tab = PlayKey, play = listOf(PlayKey, GameKey(ZIP, "$E2E_TODAY"))))
        assertRoute(GAME)
    }

    @Test
    fun `N20 a History replay's Result saved before process death returns to its origin on Done`() =
        shellRobot(pastGame, graph = replayableGraph()) {
            solveThenDie(pastSlot)

            tap(DONE)

            assertState(stacks(tab = HomeKey, home = listOf(HomeKey, HistoryKey())))
            assertRoute(HISTORY)
        }

    @Test
    fun `N20 today's Result saved before process death opens the next game on Next`() = shellRobot(playHub) {
        solveThenDie(todaySlot)
        awaitTag(RESULT_SCREEN)

        tap(NEXT)

        val next = state.currentStack.last()
        assertTrue("Next opens another game's today: $state", next is GameKey && next.gameId != ZIP && next.day == null)
        assertState(stacks(tab = PlayKey, play = listOf(PlayKey, next)))
        assertRoute(GAME)
    }

    @Test
    fun `SC8 solving deletes the save`() = shellRobot(playHub, graph = roomGraph()) {
        openGame(ZIP)
        awaitZip()
        rule.runOnIdle { MID_GAME.forEach { zip().extend(it) } }
        background()
        awaitSaved()
        foreground()

        rule.runOnIdle { ZipPlayFixture.solution.drop(MID_GAME.size).forEach { zip().extend(it) } }
        assertRoute(RESULT)

        awaitIdling { runBlocking { saves.load(todaySlot) } == null }
    }

    @Test
    fun `SC8 the newest save evicts the oldest past the cap of 8`() = shellRobot(playHub, graph = roomGraph()) {
        val older = (CAP downTo 1).map { PlaySlot.Daily(ZipDefinition.id, E2E_TODAY.minusDays(it.toLong())) }
        runBlocking {
            older.forEach { slot ->
                saves.save(slot, byteArrayOf(1), elapsedMs = 0L)
                // Room stamps saves in epoch ms: keep the seeded order strict.
                Thread.sleep(SAVE_GAP_MS)
            }
        }

        openGame(ZIP)
        awaitZip()
        rule.runOnIdle { MID_GAME.forEach { zip().extend(it) } }
        background()
        awaitSaved()

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
        switchTab(HomeKey)
        awaitText(CONTINUE_ZIP)

        graph.calendar.setAll(E2E_TODAY.plusDays(1))

        awaitTag(HOME_PLAY)
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodesWithText(CONTINUE_ZIP).fetchSemanticsNodes().isEmpty() }
        tapTab(PlayKey)
        assertState(stacks(tab = PlayKey, play = listOf(PlayKey, GameKey(ZIP))))
        assertRoute(GAME)
        awaitTag(PAST_DAY_LABEL)
        rule.onNodeWithText("Yesterday's puzzle").assertIsDisplayed()
        assertEquals("the live board keeps its day and trace", MID_GAME, rule.runOnIdle { zip().session?.path })
        assertNull("no board opened for the new day", rule.runOnIdle { store.peek(PlaySlot.Daily(ZipDefinition.id, E2E_TODAY.plusDays(1))) })
    }

    private val ShellRobot.store: PlaySessionStore get() = graph.koin.get()
    private val ShellRobot.saves: SavedPlayRepository get() = graph.koin.get()

    /** The live play of [slot] (read off the main thread only while the activity is stopped). */
    private fun ShellRobot.zip(slot: PlaySlot.Daily = todaySlot): ZipPlayController =
        checkNotNull(store.peek(slot)) { "no live zip play for ${slot.day}" }.controller as ZipPlayController

    /** Waits until [slot]'s live Zip has its session, and returns it. */
    private fun ShellRobot.awaitZip(slot: PlaySlot.Daily = todaySlot): ZipSession {
        rule.waitUntil(TIMEOUT_MS) { rule.runOnIdle { store.peek(slot) }?.let { (it.controller as ZipPlayController).session } != null }
        return checkNotNull(rule.runOnIdle { zip(slot).session })
    }

    /**
     * Waits for today's saved row after [ShellRobot.background]: the stop only launches the
     * flush, and Room commits it (save and prune, one transaction) on its own executor.
     */
    private fun ShellRobot.awaitSaved() {
        awaitIdling { runBlocking { saves.load(todaySlot) } != null }
    }

    /**
     * Solves [slot]'s board (opening today's from the hub), then dies with saved state: every
     * ViewModel and the Koin graph go (the fakes are the disk), and the navigator's saved
     * bundle comes back to a fresh shell. The restored Result has no live play behind it.
     */
    private fun ShellRobot.solveThenDie(slot: PlaySlot.Daily) {
        if (slot == todaySlot) openGame(ZIP)
        awaitZip(slot)
        rule.runOnIdle { ZipPlayFixture.solution.forEach { zip(slot).extend(it) } }
        assertRoute(RESULT)
        val saved = state
        val top = saved.currentStack.last()
        assertTrue("the solve's Result on top: $saved", top is ResultKey && top.day == "${slot.day}")

        rule.runOnIdle { rule.activity.viewModelStore.clear() }
        graph.stop()
        graph.start()
        restoreState()

        assertState(saved)
        assertRoute(RESULT)
        assertNull("no live play survives the process", rule.runOnIdle { store.peek(slot) })
    }

    private fun ShellRobot.awaitText(text: String) {
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ZipPlayController.extend(cell: Cell) = onDragEvent(DragEvent.Extend(GridCell(cell.idx / 5, cell.idx % 5)))

    /** [PAST_DAY] is a captured, cached day, so its History replay has a board to open. */
    private fun replayableGraph() = E2eKoinGraph(E2E_TODAY).apply {
        daily.setHistory(ZipDefinition.id, listOf(ZipPlayFixture.entry(E2E_TODAY), ZipPlayFixture.entry(PAST_DAY)))
        daily.setCached(ZipDefinition.id, PAST_DAY, ZipPlayFixture.envelope())
    }

    private fun stacks(
        tab: NavKey,
        home: List<NavKey> = listOf(HomeKey),
        play: List<NavKey> = listOf(PlayKey),
        history: List<NavKey> = listOf(HistoryRootKey),
        create: List<NavKey> = listOf(CreateKey),
    ) = NavigationState(tab = tab, stacks = listOf(home, play, history, create))

    private val playHub = stacks(tab = PlayKey)

    /** A History replay of [PAST_DAY], open on Home's stack. */
    private val pastGame = stacks(tab = HomeKey, home = listOf(HomeKey, HistoryKey(), GameKey(ZIP, "$PAST_DAY")))

    private val todaySlot = PlaySlot.Daily(ZipDefinition.id, E2E_TODAY)
    private val pastSlot = PlaySlot.Daily(ZipDefinition.id, PAST_DAY)

    private companion object {
        const val HISTORY = "route.history"
        const val GAME = "route.game"
        const val RESULT = "route.result"
        const val PLAY_AGAIN = "result.playAgain"
        const val RESULT_SCREEN = "result.hero.screen"
        const val NEXT = "result.next"
        const val DONE = "result.done"
        const val ZIP = "zip"
        const val HOME_PLAY = "home.today_play"
        // Home's CTA for today's saved Zip play.
        const val CONTINUE_ZIP = "Continue Zip"
        const val CAP = 8
        const val PAST_DAY_LABEL = "game.past_day"
        const val SAVE_GAP_MS = 5L
        const val TIMEOUT_MS = 5_000L
        val PAST_DAY: LocalDate = LocalDate.of(2026, 10, 30)
        val MID_GAME: List<Cell> = ZipPlayFixture.solution.take(3)
    }
}
