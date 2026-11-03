package com.ivandsky.puzzlebox.app.queens

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ivandsky.puzzlebox.app.PuzzleBoxApplication
import com.ivandsky.puzzlebox.data.repository.QueensDailyEntry
import com.ivandsky.puzzlebox.data.repository.QueensDailyRepository
import com.ivandsky.puzzlebox.data.repository.QueensToday
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.Cadence
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.queensVariantId
import com.ivandsky.puzzlebox.play.QueensPlayFlow
import com.ivandsky.puzzlebox.play.QueensRoute
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
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
 * restart the Queens game. The play entry persists the live session through
 * `rememberSaveable` as [QueensSessionCodec] bytes (the Zip PlayRoute pattern, ticket
 * 13) and adopts it on recreation: identical session (marks, undo stack, provenance)
 * and NO refetch.
 *
 * Real recreation path: [android.app.Activity.recreate] under Robolectric saves the
 * instance state and rebuilds the host with it — the same save/restore a rotation runs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class QueensRotationRestoreTest {

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

    private class CountingFakeRepository(
        private val fresh: QueensToday,
        private val history: List<QueensDailyEntry>,
    ) : QueensDailyRepository {
        var todayCalls = 0
            private set

        override suspend fun today(): QueensToday {
            todayCalls++
            return fresh
        }

        override suspend fun cached(day: LocalDate): Puzzle? = null
        override fun observeHistory(): Flow<List<QueensDailyEntry>> = flowOf(history)
    }

    private lateinit var koinApp: org.koin.core.KoinApplication

    private fun startGraph(): CountingFakeRepository {
        val repo = CountingFakeRepository(
            fresh = QueensToday.Fresh(envelope()),
            history = listOf(
                QueensDailyEntry(
                    LocalDate.now((QueensDefinition.cadence as Cadence.Daily).zone),
                    42,
                    "queens/n4",
                    0L,
                ),
            ),
        )
        koinApp = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(PuzzleBoxApplication.appModules)
        }
        koinApp.koin.loadModules(
            listOf(module { single<QueensDailyRepository> { repo } }),
            allowOverride = true,
        )
        return repo
    }

    private fun setContent(activity: ComponentActivity) {
        activity.setContent {
            PuzzleBoxTheme {
                QueensRoute(layout = PlayLayout.SinglePane, onBack = {}, onSolved = { _, _ -> })
            }
        }
    }

    private fun idle() {
        shadowOf(RuntimeEnvironment.getApplication().mainLooper).idle()
    }

    private fun awaitBoard(flow: QueensPlayFlow) {
        repeat(200) {
            idle()
            if (flow.state?.board != null) return
            Thread.sleep(10)
        }
        error("board never became ready")
    }

    @Test
    fun `a configuration change keeps the live session without a refetch`() {
        val repo = startGraph()
        val flow = koinApp.koin.get<QueensPlayFlow>()

        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        controller.setup()
        setContent(controller.get())
        idle()
        awaitBoard(flow)

        // Place marks: a MANUAL X, a queen (with its A10 auto-fill), and its undo.
        val board = flow.state!!.board!!
        board.onTap(0) // MANUAL X
        board.onTap(1) // X
        board.onTap(1) // queen at (0,1) — AUTO row fill
        board.undo() // back to: MANUAL X + queen
        idle()
        val before = flow.state!!.board!!.session
        assertEquals(1, repo.todayCalls)

        // The rotation: real save-instance-state → recreate.
        controller.get().recreate()
        idle()
        setContent(controller.get()) // the new host renders the route again
        idle()
        awaitBoard(flow)

        val after = flow.state!!.board!!.session
        assertEquals("marks, queens, undo stack, provenance identical", before, after)
        assertEquals("no refetch on recreation", 1, repo.todayCalls)

        // Progress made AFTER a restored entry must also persist — the serialization
        // callback belongs to the restored entry now (R-4: stale-bytes regression).
        val boardAfter = flow.state!!.board!!
        boardAfter.onTap(5) // another MANUAL X
        boardAfter.onTap(6) // X
        boardAfter.onTap(6) // queen at (1,2) — AUTO row fill
        idle()
        val mid = flow.state!!.board!!.session

        controller.get().recreate()
        idle()
        setContent(controller.get())
        idle()
        awaitBoard(flow)

        val final = flow.state!!.board!!.session
        assertEquals("post-restore progress survives the next recreation", mid, final)
        assertTrue("second-rotation marks present", final.markAt(Cell(5)) != null && Cell(6) in final.queens)
        assertEquals("still no refetch", 1, repo.todayCalls)

        // The critical leg (R-4): a process death while the entry is live — the Koin
        // graph is gone, so the ONLY restore path is the persisted codec bytes. The
        // session (marks, queens, undo stack, provenance) must come back with NO refetch.
        stopKoin()
        val freshRepo = startGraph() // a brand-new graph, a brand-new flow
        val freshFlow = koinApp.koin.get<QueensPlayFlow>()
        controller.get().recreate()
        idle()
        setContent(controller.get())
        idle()
        awaitBoard(freshFlow)
        assertEquals("process death restores the exact live session", mid, freshFlow.state!!.board!!.session)
        assertEquals("no refetch after process death — restore came from codec bytes", 0, freshRepo.todayCalls)
    }
}
