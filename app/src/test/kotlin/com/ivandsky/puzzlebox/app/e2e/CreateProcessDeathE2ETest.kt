package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.data.di.dataModule
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft
import com.ivandsky.puzzlebox.games.queens.editor.QueensEditor
import com.ivandsky.puzzlebox.games.queens.QueensVariants
import java.time.Instant

/**
 * Ticket 17 over the real shell and a file-backed Room drafts store: a Queens draft painted
 * three times and undone once keeps its board and both undo and redo across a tab switch, a
 * rotation, back and reopen, and an app stop followed by process death. The reopened
 * draft then undoes exactly its two remaining paints.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
// A real phone's height: Robolectric's default 470 dp leaves the Studio's board slot no room for a grid.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class CreateProcessDeathE2ETest {

    /** Every "process" Room was opened in; each relaunch opens the same database file anew. */
    private val processes = mutableListOf<KoinApplication>()

    @After
    fun closeProcesses() {
        processes.forEach(KoinApplication::close)
    }

    /**
     * The E2E graph with the production Room [DraftRepository] in place of the fake: each graph
     * start resolves it from a fresh data graph, so only what reached the disk survives a
     * [ShellRobot.relaunch].
     */
    private fun roomGraph() = E2eKoinGraph(
        E2E_TODAY,
        overrides = module {
            single<DraftRepository> {
                koinApplication {
                    androidContext(RuntimeEnvironment.getApplication())
                    modules(dataModule)
                }.also(processes::add).koin.get()
            }
        },
    )

    @Test
    fun `a painted draft keeps its board and undo history across tab switch, rotation, back and process death`() =
        shellRobot(graph = roomGraph()) {
            tapTab(CreateKey)
            tap("create.empty.game.queens")
            awaitTag(cell(0))
            val blank = regionOf(cell(0))
            // A new draft opens in Crown mode (36); painting needs Paint.
            tap("queens.editor.mode.paint")
            (0..2).forEach { i ->
                tap(cell(i))
                tap("queens.editor.ring.slot.${i + 1}")
            }
            tap(UNDO)
            val board = (0..2).map { regionOf(cell(it)) }
            assertNotEquals("cell 0 is painted", blank, board[0])
            assertNotEquals("cell 1 is painted", blank, board[1])
            assertEquals("cell 2's paint is undone", blank, board[2])
            assertKept(board, "on paint")

            tapTab(HomeKey)
            tapTab(CreateKey)
            awaitTag(cell(0))
            assertKept(board, "after a tab switch")

            restoreState()
            awaitTag(cell(0))
            assertKept(board, "after a rotation")

            back()
            awaitStoredHistory(done = 2, undone = 1)
            reopen()
            assertKept(board, "after back and reopen")

            background()
            awaitStoredHistory(done = 2, undone = 1)
            foreground()
            relaunch()
            reopen()
            assertKept(board, "after process death")

            tap(UNDO)
            tap(UNDO)
            assertEquals("two undos clear the board", blank, regionOf(cell(0)))
            rule.onNodeWithTag(UNDO).assertIsNotEnabled()
            rule.onNodeWithTag(REDO).assertIsEnabled()
        }

    @Test
    fun `a passed test solve survives process death, so the first board edit clears it`() =
        shellRobot(graph = roomGraph()) {
            // Seeded, not played: the Queens play board has no per-cell tags to solve through.
            val editor = checkNotNull(graph.koin.get<GameRegistry>()[GameId(QUEENS)]?.editor)
            val solved = EditorDraft(QueensEditor, DraftState(QueensDraft(SIZE, regions(UNIQUE))))
            runBlocking {
                graph.koin.get<DraftRepository>().upsert(
                    Draft(
                        id = "passed",
                        gameId = GameId(QUEENS),
                        variant = QueensVariants.variant(SIZE).id,
                        draftFormatVersion = editor.draftFormatVersion,
                        blob = solved.encode(),
                        title = "",
                        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
                        testSolve = TestSolve(solved.layoutKey, timeMs = 102_000, hints = 0),
                        reservedCode = null,
                        imported = false,
                        authorName = "",
                        note = "",
                    ),
                )
            }

            background()
            foreground()
            relaunch()
            reopen()
            tap(cell(0))
            tap("queens.editor.ring.slot.0")

            awaitTag("create.toast.testCleared")
        }


    /** The Studio shows [board] on cells 0..2, and both Undo and Redo have a step. */
    private fun ShellRobot.assertKept(board: List<String>, moment: String) {
        assertEquals("the board $moment", board, (0..2).map { regionOf(cell(it)) })
        rule.onNodeWithTag(UNDO).assertIsEnabled()
        rule.onNodeWithTag(REDO).assertIsEnabled()
    }

    /** Opens the one draft from the Create root. */
    private fun ShellRobot.reopen() {
        tapTab(CreateKey)
        tap(ROW)
        awaitTag(cell(0))
    }

    /** Waits until the stored row's blob carries [done] undo and [undone] redo steps. */
    private fun ShellRobot.awaitStoredHistory(done: Int, undone: Int) {
        val drafts = graph.koin.get<DraftRepository>()
        val editor = checkNotNull(graph.koin.get<GameRegistry>()[GameId(QUEENS)]?.editor)
        rule.waitUntil(TIMEOUT_MS) {
            val row = runBlocking { drafts.observeAll().first().singleOrNull() } ?: return@waitUntil false
            val history = editor.decodeDraft(row.blob).getOrNull()?.history ?: return@waitUntil false
            history.done.size == done && history.undone.size == undone
        }
    }

    private fun ShellRobot.regionOf(tag: String): String =
        rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.StateDescription]

    private fun cell(index: Int) = "queens.editor.cell.$index"

    /** Row-major region ids from letter rows ('A' = 0), space-separated. */
    private fun regions(rows: String): List<Int> = rows.filter { it != ' ' }.map { it - 'A' }

    private companion object {
        const val QUEENS = "queens"
        const val SIZE = 6

        /** One solution on a 6×6 board. */
        const val UNIQUE = "BBBBAA BBBBCA EBBDCA EEDDDD EEFFDF EEFFFF"
        const val ROW = "create.draft.row"
        const val UNDO = "create.tool.undo"
        const val REDO = "create.tool.redo"
        const val TIMEOUT_MS = 5_000L
    }
}
