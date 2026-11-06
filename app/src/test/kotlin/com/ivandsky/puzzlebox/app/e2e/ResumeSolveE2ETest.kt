package com.ivandsky.puzzlebox.app.e2e

import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.leaderboard.LeaderboardSubmitWatcher
import com.ivandsky.puzzlebox.app.play.ZipPlayFixture
import com.ivandsky.puzzlebox.data.di.dataModule
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipSession
import com.ivandsky.puzzlebox.games.zipui.ZipPlayController
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * SC6 / F4 over the real shell and a file-backed Room database: a game left mid-play, killed
 * without saved state and finished in the next process records its play time only (the
 * time before the kill plus the time after the resume, never the dead time between), and
 * the signed-in player's record reaches the leaderboard exactly once.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = android.app.Application::class)
class ResumeSolveE2ETest {

    /** One data graph per simulated process; each opens the same database file. */
    private val processes = mutableListOf<KoinApplication>()

    @After
    fun closeProcesses() {
        processes.forEach(KoinApplication::close)
    }

    private fun roomGraph() = E2eKoinGraph(
        E2E_TODAY,
        overrides = module {
            single(named(ROOM)) {
                val registry = get<GameRegistry>()
                koinApplication {
                    androidContext(RuntimeEnvironment.getApplication())
                    modules(dataModule, module { single { registry } })
                }.also(processes::add).koin
            }
            single<SavedPlayRepository> { get<Koin>(named(ROOM)).get() }
            single<CompletionRepository> { get<Koin>(named(ROOM)).get() }
        },
    ).apply {
        // A numbered daily (#592): only a numbered puzzle has a leaderboard board to submit to.
        daily.setHistory(ZipDefinition.id, listOf(ZipPlayFixture.entry(E2E_TODAY)))
    }

    @Test
    fun `SC6 a game resumed after a kill records its play time and submits once`() = shellRobot(playHub, graph = roomGraph()) {
        openGame(ZIP)
        awaitZip()
        val firstMoveAt = System.currentTimeMillis()
        rule.runOnIdle { MID_GAME.forEach { zip().extend(it) } }
        awaitWallClock(since = firstMoveAt, PLAYED_MS)

        background()
        // The flush is launched on stop; Room commits on its own executor, so wait for the row.
        rule.waitUntil(TIMEOUT_MS) { runBlocking { saves.load(todaySlot) } != null }
        val killed = checkNotNull(zip().session)
        // The app is gone: none of this is play time. Wait while stopped (the clock is folded),
        // and foreground only to hand the activity to relaunch().
        awaitWallClock(since = System.currentTimeMillis(), DEAD_MS)
        foreground()

        graph.identity.currentIdentity = Identity(uid = "u1", displayName = "Player", photoUrl = null)
        relaunch()
        // Only PuzzleBoxApplication.onCreate starts the watcher; the E2E graph starts it here.
        graph.koin.get<LeaderboardSubmitWatcher>().start()
        openGame(ZIP)
        awaitZip()
        val resumedAt = System.currentTimeMillis()
        rule.runOnIdle { ZipPlayFixture.solution.drop(MID_GAME.size).forEach { zip().extend(it) } }
        awaitTag(RESULT)
        val solvedAt = System.currentTimeMillis()

        // The record write is fire-and-observe on Room's executor: wait for the row.
        rule.waitUntil(TIMEOUT_MS) { zipRecords().isNotEmpty() }
        val record = zipRecords().single()
        assertTrue("the first solve is the record", record.isRecord)
        assertEquals(
            "time before the kill plus time after the resume (±1 s)",
            (killed.elapsedMs + (solvedAt - resumedAt)).toDouble(),
            record.time.toDouble(),
            1_000.0,
        )

        rule.waitUntil(TIMEOUT_MS) { submittedZip().isNotEmpty() }
        awaitWallClock(since = System.currentTimeMillis(), SETTLE_MS) // a duplicate submit would be launched by now
        assertEquals("submitted exactly once", 1, submittedZip().size)
        assertEquals(record.time, submittedZip().single().timeMs)
    }

    private fun ShellRobot.submittedZip() =
        graph.leaderboard.submitted.toList().filter { it.puzzleRef.startsWith("zip/$E2E_TODAY") }

    private fun ShellRobot.zipRecords() = runBlocking { graph.koin.get<CompletionRepository>().observeRecords().first() }
        .filter { it.puzzleRef.gameId == ZipDefinition.id && (it.puzzleRef.key as? PuzzleKey.Daily)?.date == E2E_TODAY }

    /** Lets [ms] of wall clock pass (Zip's clock is the wall clock) while the UI keeps idling. */
    private fun ShellRobot.awaitWallClock(since: Long, ms: Long) {
        rule.waitUntil(ms + TIMEOUT_MS) { System.currentTimeMillis() - since >= ms }
    }

    private val ShellRobot.store: PlaySessionStore get() = graph.koin.get()
    private val ShellRobot.saves: SavedPlayRepository get() = graph.koin.get()

    private fun ShellRobot.zip(): ZipPlayController =
        checkNotNull(store.peek(todaySlot)) { "no live zip play for $E2E_TODAY" }.controller as ZipPlayController

    /** Waits until today's live Zip has its session, and returns it. */
    private fun ShellRobot.awaitZip(): ZipSession {
        rule.waitUntil(TIMEOUT_MS) { rule.runOnIdle { store.peek(todaySlot) }?.let { (it.controller as ZipPlayController).session } != null }
        return checkNotNull(rule.runOnIdle { zip().session })
    }

    private fun ZipPlayController.extend(cell: Cell) = onDragEvent(DragEvent.Extend(GridCell(cell.idx / 5, cell.idx % 5)))

    private fun stacks(
        tab: NavKey,
        home: List<NavKey> = listOf(HomeKey),
        play: List<NavKey> = listOf(PlayKey),
        profile: List<NavKey> = listOf(ProfileKey),
    ) = NavigationState(tab = tab, stacks = listOf(home, play, profile))

    private val playHub = stacks(tab = PlayKey)
    private val todaySlot = PlaySlot(ZipDefinition.id, E2E_TODAY)

    private companion object {
        const val ROOM = "room"
        const val RESULT = "route.result"
        const val ZIP = "zip"
        const val PLAYED_MS = 1_500L
        const val DEAD_MS = 2_500L
        const val SETTLE_MS = 300L
        const val TIMEOUT_MS = 5_000L
        val MID_GAME: List<Cell> = ZipPlayFixture.solution.take(3)
    }
}
