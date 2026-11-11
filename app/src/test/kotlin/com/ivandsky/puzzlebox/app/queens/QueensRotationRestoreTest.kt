package com.ivandsky.puzzlebox.app.queens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.ivandsky.puzzlebox.app.PuzzleBoxApplication
import com.ivandsky.puzzlebox.app.play.freshDailyForAllGames
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.today
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.queensVariantId
import com.ivandsky.puzzlebox.games.queensui.QueensPlayController
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.navigation.playEntry
import org.junit.After
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * R-4 (fix round 4, critical — data loss): a configuration change (rotation) must NOT
 * restart the Queens game. The hub pushes Queens' game key; its slot's live play lives
 * in the [PlaySessionStore] single, so a recreation reopens the same session (marks,
 * undo stack, provenance) with NO refetch. After a process death the flushed saved-play
 * row (real Room) is the only restore path, and it too restores with no refetch.
 *
 * Real recreation path: [android.app.Activity.recreate] under Robolectric saves the
 * instance state and rebuilds the host with it — the same save/restore a rotation runs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class QueensRotationRestoreTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @After
    fun tearDown() {
        stopKoin()
    }

    /** 4×4 fixture, regions = rows. */
    private fun envelope(): Puzzle {
        val puzzle = QueensPuzzle(
            size = 4,
            regions = List(16) { it / 4 },
            solution = listOf(1, 6, 11, 12).map(::Cell),
        )
        return Puzzle(
            gameId = QueensDefinition.id,
            variant = queensVariantId(4),
            formatVersion = QueensCodec.VERSION,
            payload = QueensCodec.encode(puzzle),
        )
    }

    private lateinit var koinApp: org.koin.core.KoinApplication

    private fun startGraph(): FakeDailyRepository {
        // The hub prefetches every game's today on start; Queens gets this test's board.
        val repo = freshDailyForAllGames().apply {
            setToday(QueensDefinition.id, DailyToday.Fresh(envelope()))
            setHistory(
                QueensDefinition.id,
                listOf(DailyEntry(QueensDefinition.id, QueensDefinition.cadence.today(), 42, "queens/n4", 0L)),
            )
        }
        koinApp = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(PuzzleBoxApplication.appModules)
        }
        koinApp.koin.loadModules(
            listOf(
                module {
                    single<DailyRepository> { repo }
                    // The game page's board reads the leaderboard and signs in through these; Firebase is not started here.
                    single<com.ivandsky.puzzlebox.data.identity.IdentityService> { com.ivandsky.puzzlebox.testing.repository.FakeIdentityService() }
                    single<com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository> { com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository(emptyMap()) }
                    single<com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider> {
                        object : com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider {
                            override suspend fun idToken(activityContext: android.content.Context) =
                                com.ivandsky.puzzlebox.auth.GoogleIdToken.Unavailable(com.ivandsky.puzzlebox.auth.GoogleIdToken.Unavailable.Reason.CANCELLED)
                        }
                    }
                    single<com.ivandsky.puzzlebox.data.repository.GameSettingsRepository> { com.ivandsky.puzzlebox.testing.repository.FakeGameSettingsRepository() }
                    // Every tutorial seen: this path is about the board; the lessons have their own tests.
                    single<com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository> {
                        com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository(
                            seen = setOf(
                                com.ivandsky.puzzlebox.games.zip.ZipDefinition.id,
                                com.ivandsky.puzzlebox.games.tango.TangoDefinition.id,
                                QueensDefinition.id,
                            ),
                        )
                    }
                },
            ),
            allowOverride = true,
        )
        return repo
    }

    private val slot = PlaySlot.Daily(QueensDefinition.id, QueensDefinition.cadence.today())

    /** The Play tab's stack; a field, so it outlives recreations as the shell's saved state does. */
    private var navigator = Navigator(NavigationState(tab = PlayKey, stacks = listOf(listOf(PlayKey))))

    private fun setContent(activity: ComponentActivity) {
        activity.setContent {
            PuzzleBoxTheme {
                NavDisplay(
                    backStack = navigator.state.currentStack,
                    onBack = { navigator.pop() },
                    entryProvider = entryProvider { playEntry(navigator, PlayLayout.SinglePane, onOpenGameHistory = {}) },
                )
            }
        }
    }

    private fun idle() {
        shadowOf(RuntimeEnvironment.getApplication().mainLooper).idle()
    }

    private fun awaitBoard(store: PlaySessionStore) {
        repeat(200) {
            idle()
            // The load is launched from a composition effect: drive frames, not just the looper.
            composeRule.waitForIdle()
            if ((store.peek(slot)?.controller as? QueensPlayController)?.board != null) return
            Thread.sleep(10)
        }
        error("board never became ready")
    }

    private fun queens(store: PlaySessionStore): QueensPlayController =
        store.peek(slot)!!.controller as QueensPlayController

    /** A recreation pauses the clock, which banks the running window — so elapsed time may
     *  only grow across it; everything else (marks, queens, history) must be identical. */
    private fun assertSameButClock(message: String, expected: QueensSession, actual: QueensSession) {
        assertTrue("$message: banked time never rewinds", actual.elapsedMs >= expected.elapsedMs)
        assertEquals(message, expected, actual.copy(elapsedMs = expected.elapsedMs))
    }

    @Test
    fun `a configuration change keeps the live session without a refetch`() {
        val repo = startGraph()
        val flow = koinApp.koin.get<PlaySessionStore>()

        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        controller.setup()
        setContent(controller.get())
        idle()
        // The hub's start prefetch is its own fetch; only opening the board is counted below.
        val prefetched = repo.todayCalls(QueensDefinition.id)
        // The Play tab opens the hub; Queens is one of its games (the fix under test
        // is that it no longer bypasses the hub with its own stack).
        composeRule.onNodeWithTag("library.row.queens").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(10_000) { composeRule.onAllNodes(hasTestTag("library.play") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("library.play").performScrollTo().performClick()
        idle()
        awaitBoard(flow)

        // Place marks: a MANUAL X, a queen (with its A10 auto-fill), and its undo.
        val board = queens(flow).board!!
        board.onTap(0) // MANUAL X
        board.onTap(1) // X
        board.onTap(1) // queen at (0,1) — AUTO row fill
        board.undo() // back to: MANUAL X + queen
        idle()
        val before = queens(flow).board!!.session
        val opened = prefetched + 1
        assertEquals("opening the board fetches once", opened, repo.todayCalls(QueensDefinition.id))

        // The rotation: real save-instance-state → recreate.
        controller.get().recreate()
        idle()
        setContent(controller.get()) // the new host renders the route again
        idle()
        awaitBoard(flow)

        val after = queens(flow).board!!.session
        assertSameButClock("marks, queens, undo history, provenance identical", before, after)
        assertEquals("no refetch on recreation", opened, repo.todayCalls(QueensDefinition.id))

        // Progress made AFTER a restored entry must also persist — the serialization
        // callback belongs to the restored entry now (R-4: stale-bytes regression).
        val boardAfter = queens(flow).board!!
        boardAfter.onTap(5) // another MANUAL X
        boardAfter.onTap(6) // X
        boardAfter.onTap(6) // queen at (1,2) — AUTO row fill
        idle()
        val mid = queens(flow).board!!.session

        controller.get().recreate()
        idle()
        setContent(controller.get())
        idle()
        awaitBoard(flow)

        val final = queens(flow).board!!.session
        assertSameButClock("post-restore progress survives the next recreation", mid, final)
        assertTrue("second-rotation marks present", final.markAt(Cell(5)) != null && Cell(6) in final.queens)
        assertEquals("still no refetch", opened, repo.todayCalls(QueensDefinition.id))

        // The critical leg (R-4): a process death while the entry is live — the activity,
        // its ViewModels and the Koin graph are all gone. The shell's stop flush lands the
        // session in the saved-play row, and the restored stack (Play → Queens) reopens it
        // from there: marks, queens, undo stack and provenance come back with NO refetch.
        val saved = Bundle()
        controller.pause().stop().saveInstanceState(saved)
        val flushed = flow.flush()
        repeat(200) { if (!flushed.isCompleted) { idle(); Thread.sleep(10) } }
        assertTrue("the stop flush landed", flushed.isCompleted)
        controller.destroy()
        stopKoin()
        val freshRepo = startGraph() // a brand-new graph, a brand-new store
        val freshStore = koinApp.koin.get<PlaySessionStore>()
        navigator = Navigator(
            NavigationState(tab = PlayKey, stacks = listOf(listOf<NavKey>(PlayKey, GameKey(QueensDefinition.id.value)))),
        )
        val reborn = Robolectric.buildActivity(ComponentActivity::class.java).setup(saved)
        setContent(reborn.get())
        idle()
        awaitBoard(freshStore)
        assertSameButClock("process death restores the exact live session", mid, queens(freshStore).board!!.session)
        assertEquals("no refetch after process death — restore came from the saved row", 0, freshRepo.todayCalls(QueensDefinition.id))
    }
}
