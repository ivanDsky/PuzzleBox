package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
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

/**
 * Ticket 17 over the real shell and a file-backed Room drafts store: a Queens draft painted
 * three times and undone once keeps its board and both undo and redo across a tab switch, a
 * rotation, back and reopen, and an app stop followed by process death. The reopened
 * draft then undoes exactly its two remaining paints.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = android.app.Application::class)
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

    private companion object {
        const val QUEENS = "queens"
        const val ROW = "create.draft.row"
        const val UNDO = "create.tool.undo"
        const val REDO = "create.tool.redo"
        const val TIMEOUT_MS = 5_000L
    }
}
