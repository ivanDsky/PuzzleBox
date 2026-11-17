package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import android.view.View
import android.view.accessibility.AccessibilityNodeProvider
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.gameui.hidden.HiddenBoard
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.ivandsky.puzzlebox.games.tango.TangoSession

/**
 * Tango's board before play shows the given suns and moons and the = and x marks and nothing else. A Tango
 * puzzle carries no solution; what its payload may carry beyond the board is dropped all the same.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class TangoHiddenBoardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var view: View

    private val envelope = TangoPlayFixture.envelope()

    @Test
    fun `the board is built from the givens and marks alone`() {
        val session = checkNotNull(tangoGivensSession(envelope))
        assertEquals(TangoSession(ref = null, puzzle = TangoPlayFixture.puzzle), session)
        assertEquals(TangoPlayFixture.puzzle.givens, session.puzzle.givens)
        assertEquals(emptyMap<Int, Any>(), session.placements)
    }

    @Test
    fun `nothing a payload carries past the board reaches the session`() {
        val tailed = TangoPlayFixture.envelope(TangoPlayFixture.puzzle.copy(unknownTail = byteArrayOf(1, 2, 3)))
        assertNotEquals(envelope.payload.toList(), tailed.payload.toList())
        assertEquals(0, checkNotNull(tangoGivensSession(tailed)).puzzle.unknownTail.size)
        assertEquals(tangoGivensSession(envelope), tangoGivensSession(tailed))
    }

    @Test
    fun `the slot takes the board's shape`() {
        val board = checkNotNull(TangoReplayBoard.givens(envelope))
        assertEquals(6 to 6, board.rows to board.cols)
    }

    /** Every text and description in the tree as a reader of the screen takes it: merged. */
    private fun spoken(): List<String> {
        val out = ArrayList<String>()
        fun walk(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.let(out::addAll)
            node.config.getOrNull(SemanticsProperties.Text)?.forEach { out += it.text }
            node.children.forEach(::walk)
        }
        walk(composeRule.onRoot().fetchSemanticsNode())
        return out
    }

    /**
     * What the platform will describe to an accessibility service: the description and text of every node it
     * agrees to hand out, asking for each node of the unmerged tree by its id. The slot clears what is under it
     * rather than removing it, so the board's nodes are still in that tree; none of them is handed out.
     */
    private fun handedOut(): List<String> {
        val root = composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        val out = ArrayList<String>()
        composeRule.runOnUiThread {
            val provider = checkNotNull(view.accessibilityNodeProvider)
            fun ask(node: SemanticsNode, id: Int) {
                // A node that merges its children is described by two stand-ins the tree does not list.
                for (asked in listOf(id, id + ROLE_STAND_IN, id + DESCRIPTION_STAND_IN)) {
                    val info = provider.createAccessibilityNodeInfo(asked) ?: continue
                    info.contentDescription?.let { out += it.toString() }
                    info.text?.let { out += it.toString() }
                }
                node.children.forEach { ask(it, it.id) }
            }
            ask(root, AccessibilityNodeProvider.HOST_VIEW_ID)
        }
        return out
    }

    /** The cell has room past its text: a described cell its own text covers whole is handed out as text alone. */
    @Test
    fun `shown bare, a cell that says what it holds is handed to a screen reader`() {
        composeRule.setContent {
            view = LocalView.current
            PuzzleBoxTheme {
                Box(Modifier.size(96.dp).semantics { contentDescription = "Cell" }.clickable { }) { Text("7") }
            }
        }
        composeRule.waitForIdle()
        assertEquals(listOf("Cell", "7"), handedOut())
    }

    private fun show(sharp: Boolean) {
        val board = checkNotNull(TangoReplayBoard.givens(envelope))
        composeRule.setContent {
            view = LocalView.current
            PuzzleBoxTheme { HiddenBoard(board, sharp = sharp) }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `blurred, the screen reader hears one line and no cell`() {
        show(sharp = false)
        assertEquals(listOf("Board hidden until you play"), handedOut())
        assertEquals(listOf("Board hidden until you play"), spoken())
    }

    @Test
    fun `sharp, the screen reader still hears one line and no cell`() {
        show(sharp = true)
        assertEquals(listOf("Board"), handedOut())
        assertEquals(listOf("Board"), spoken())
    }

    @Test
    fun `another game's puzzle and a broken payload draw nothing`() {
        assertNull(TangoReplayBoard.givens(Puzzle(GameId("someone-else"), envelope.variant, envelope.formatVersion, envelope.payload)))
        assertNull(TangoReplayBoard.givens(Puzzle(envelope.gameId, envelope.variant, envelope.formatVersion, byteArrayOf(9, 9, 9))))
    }

    private companion object {
        const val ROLE_STAND_IN = 1_000_000_000
        const val DESCRIPTION_STAND_IN = 2_000_000_000
    }
}
