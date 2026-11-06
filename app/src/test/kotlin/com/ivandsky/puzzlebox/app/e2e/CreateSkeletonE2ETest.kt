package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Ticket 01's walking skeleton over the real shell: the Create tab's New puzzle sheet
 * (Queens, then 4×4) opens a blank Queens 4×4 in the Studio (verdict Incomplete), a tap
 * selects a cell and a ring pick paints it, the draft is listed on the Create root, and
 * after a relaunch the draft reopens with the same regions.
 * The drafts store is the fake "disk" that outlives the relaunch.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = android.app.Application::class)
class CreateSkeletonE2ETest {

    private val drafts = FakeDraftRepository()

    @Test
    fun `a new Queens draft is painted, listed, and reopens with its regions after a relaunch`() =
        shellRobot(graph = E2eKoinGraph(E2E_TODAY, overrides = module { single<DraftRepository> { drafts } })) {
            tapTab(CreateKey)
            assertRoute(CREATE)
            tap("create.new")
            tap("create.new.game.queens")
            tap("create.new.size.queens/n4")
            awaitTag(BOARD)
            awaitTag(CELL_0)
            assertRoute(EDITOR)
            // Queens 4×4: 16 cells, all unpainted, and nothing to check yet (the verdict lands off the main thread).
            rule.onAllNodesWithTag("queens.editor.cell.15", useUnmergedTree = true).assertCountEquals(1)
            rule.onAllNodesWithTag("queens.editor.cell.16", useUnmergedTree = true).assertCountEquals(0)
            rule.waitUntil(TIMEOUT_MS) {
                val incomplete = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Incomplete")
                rule.onAllNodes(hasTestTag(PILL) and incomplete, useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            val blank = regionOf(CELL_0)

            tap(CELL_0)
            tap(RING_NEXT)
            val painted = regionOf(CELL_0)
            assertNotEquals("the tap paints the cell", blank, painted)
            assertEquals(1, drafts.rows.size)

            back()
            assertRoute(CREATE)
            awaitTag(ROW)
            rule.onAllNodesWithTag(ROW).assertCountEquals(1)

            relaunch()
            tapTab(CreateKey)
            tap(ROW)
            awaitTag(CELL_0)
            assertEquals("the reopened draft keeps its regions", painted, regionOf(CELL_0))
        }

    private fun ShellRobot.regionOf(tag: String): String =
        rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.StateDescription]

    private companion object {
        const val CREATE = "route.create"
        const val EDITOR = "route.editor"
        const val BOARD = "create.editor.board"
        const val PILL = "create.editor.pill"
        const val ROW = "create.draft.row"
        const val CELL_0 = "queens.editor.cell.0"
        const val TIMEOUT_MS = 5_000L
        const val RING_NEXT = "queens.editor.ring.slot.1"
    }
}
