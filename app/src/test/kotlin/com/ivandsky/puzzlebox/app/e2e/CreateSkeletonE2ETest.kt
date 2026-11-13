package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.app.DrainTestApplication
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
 * Ticket 01's walking skeleton over the real shell: on the first-time Create root, one tap on
 * Queens in the empty state's game list opens a blank Queens at its default size (8×8) in the
 * Studio (verdict Incomplete), in Paint mode a tap selects a cell and a ring pick paints it, the draft is
 * listed on the Create root, and after a relaunch the draft reopens with the same regions.
 * The drafts store is the fake "disk" that outlives the relaunch.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
// A real phone's height: Robolectric's default 470 dp leaves the Studio's board slot no room for a grid.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class CreateSkeletonE2ETest {

    private val drafts = FakeDraftRepository()

    @Test
    fun `a new Queens draft is painted, listed, and reopens with its regions after a relaunch`() =
        shellRobot(graph = E2eKoinGraph(E2E_TODAY, overrides = module { single<DraftRepository> { drafts } })) {
            tapTab(CreateKey)
            assertRoute(CREATE)
            tap("create.empty.game.queens")
            awaitTag(BOARD)
            awaitTag(CELL_0)
            assertRoute(EDITOR)
            // Queens 8×8: 64 cells, all unpainted, and nothing to check yet (the verdict lands off the main thread).
            rule.onAllNodesWithTag("queens.editor.cell.63", useUnmergedTree = true).assertCountEquals(1)
            rule.onAllNodesWithTag("queens.editor.cell.64", useUnmergedTree = true).assertCountEquals(0)
            rule.waitUntil(TIMEOUT_MS) {
                val incomplete = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Incomplete")
                rule.onAllNodes(hasTestTag(PILL) and incomplete, useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            val blank = regionOf(CELL_0)
            assertEquals("a new draft starts unpainted", "unpainted", blank)

            tap(PAINT_MODE) // a new draft opens in Crown (36)
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
        const val PAINT_MODE = "queens.editor.mode.paint"
    }
}
