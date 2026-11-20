package com.ivandsky.puzzlebox.gameui.hidden

import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityNodeProvider
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.SHIMMER_TAG
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The hidden board's promises, on a board that would give everything away: each of its cells is spoken, takes
 * taps and long presses, and counts every touch it sees. Blurred or sharp, a screen reader is handed one line and
 * no touch reaches a cell, while a drag that starts on the board still scrolls the page it is in.
 *
 * "Handed" is asked of the platform itself, node by node ([handedOut]); shown bare, the same board's cell is
 * handed out. The slot clears what is under it rather than removing it: a test that reads the unmerged tree
 * still finds the board's nodes there, which is why no solution may be in the board to begin with (each game's
 * own HiddenBoardTest).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h1200dp-mdpi")
class HiddenBoardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var taps = 0
    private var presses = 0
    private var touches = 0
    private lateinit var view: View

    /** A board that leaks: every cell has text, a description, a click and a long press, and sees each touch. */
    private fun leakyBoard(rows: Int = 6, cols: Int = 6) = GivensBoard(rows, cols) { modifier ->
        Box(
            modifier
                .fillMaxSize()
                .semantics { contentDescription = "Row 1, column 1, queen" }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                            touches++
                        }
                    }
                }
                .combinedClickable(onLongClick = { presses++ }) { taps++ },
        ) {
            Text("7")
        }
    }

    private fun setAnimatorScale(scale: Float) {
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    @After
    fun resetScale() = setAnimatorScale(1f)

    private fun show(width: Int, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            view = LocalView.current
            PuzzleBoxTheme { Box(Modifier.width(width.dp)) { content() } }
        }
        composeRule.waitForIdle()
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
     * agrees to hand out, asking for each node of the unmerged tree by its id.
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

    @Test
    fun `shown bare, the board's cell is handed to a screen reader`() {
        show(360) { leakyBoard().content(Modifier) }
        assertEquals(listOf("Row 1, column 1, queen", "7"), handedOut())
        assertEquals(listOf("Row 1, column 1, queen", "7"), spoken())
    }

    @Test
    fun `blurred, a screen reader is handed one line and no cell`() {
        show(360) { HiddenBoard(leakyBoard(), sharp = false) }
        assertEquals(listOf("Board hidden until you play"), handedOut())
        assertEquals(listOf("Board hidden until you play"), spoken())
    }

    @Test
    fun `sharp, a screen reader is still handed one line and no cell`() {
        show(360) { HiddenBoard(leakyBoard(), sharp = true) }
        assertEquals(listOf("Board"), handedOut())
        assertEquals(listOf("Board"), spoken())
    }

    @Test
    fun `blurred, no touch reaches the board`() {
        show(360) { HiddenBoard(leakyBoard(), sharp = false) }
        composeRule.onNodeWithContentDescription("Board hidden until you play").performTouchInput {
            click()
            click(topLeft)
            longClick()
            swipeDown()
        }
        composeRule.waitForIdle()
        assertNothingReachedTheBoard()
    }

    @Test
    fun `sharp, no touch reaches the board either`() {
        show(360) { HiddenBoard(leakyBoard(), sharp = true) }
        composeRule.onNodeWithContentDescription("Board").performTouchInput {
            click()
            click(bottomRight)
            longClick()
        }
        composeRule.waitForIdle()
        assertNothingReachedTheBoard()
    }

    @Test
    fun `the reveal ends with the sharp board alone, still silent and untouchable`() {
        setAnimatorScale(1f)
        var sharp by mutableStateOf(false)
        show(360) { HiddenBoard(leakyBoard(), sharp = sharp) }
        sharp = true
        composeRule.waitForIdle()
        assertEquals(listOf("Board"), handedOut())
        assertEquals(listOf("Board"), spoken())
        composeRule.onNodeWithContentDescription("Board").performTouchInput { click() }
        assertNothingReachedTheBoard()
    }

    @Test
    fun `a square board fills a phone's width`() {
        show(360) { HiddenBoard(leakyBoard(), sharp = false) }
        composeRule.onNodeWithContentDescription("Board hidden until you play")
            .assertWidthIsEqualTo(360.dp)
            .assertHeightIsEqualTo(360.dp)
    }

    @Test
    fun `a square board stops at 400 dp in a wide pane`() {
        show(1000) { HiddenBoard(leakyBoard(), sharp = false) }
        composeRule.onNodeWithContentDescription("Board hidden until you play")
            .assertWidthIsEqualTo(400.dp)
            .assertHeightIsEqualTo(400.dp)
    }

    @Test
    fun `a wide board keeps its shape, 400 dp high at most`() {
        show(1000) { HiddenBoard(leakyBoard(rows = 4, cols = 8), sharp = false) }
        composeRule.onNodeWithContentDescription("Board hidden until you play")
            .assertWidthIsEqualTo(800.dp)
            .assertHeightIsEqualTo(400.dp)
    }

    @Test
    fun `a wide board on a phone is as wide as the phone`() {
        show(360) { HiddenBoard(leakyBoard(rows = 4, cols = 8), sharp = true) }
        composeRule.onNodeWithContentDescription("Board")
            .assertWidthIsEqualTo(360.dp)
            .assertHeightIsEqualTo(180.dp)
    }

    @Test
    fun `a tall board is narrower than the pane`() {
        show(1000) { HiddenBoard(leakyBoard(rows = 8, cols = 6), sharp = false) }
        composeRule.onNodeWithContentDescription("Board hidden until you play")
            .assertWidthIsEqualTo(300.dp)
            .assertHeightIsEqualTo(400.dp)
    }

    @Test
    fun `a larger max side lets a square board grow past 400 dp, to the width it is given`() {
        show(1000) { HiddenBoard(leakyBoard(), sharp = false, maxSide = 600.dp) }
        composeRule.onNodeWithContentDescription("Board hidden until you play")
            .assertWidthIsEqualTo(600.dp)
            .assertHeightIsEqualTo(600.dp)
    }

    @Test
    fun `a larger max side never makes the board wider than it is given`() {
        show(500) { HiddenBoard(leakyBoard(), sharp = true, maxSide = 600.dp) }
        composeRule.onNodeWithContentDescription("Board")
            .assertWidthIsEqualTo(500.dp)
            .assertHeightIsEqualTo(500.dp)
    }

    @Test
    fun `a wide board keeps its shape under a larger max side`() {
        show(1200) { HiddenBoard(leakyBoard(rows = 4, cols = 8), sharp = false, maxSide = 500.dp) }
        composeRule.onNodeWithContentDescription("Board hidden until you play")
            .assertWidthIsEqualTo(1000.dp)
            .assertHeightIsEqualTo(500.dp)
    }

    @Test
    fun `loading takes the same larger max side as the board`() {
        setAnimatorScale(0f)
        show(1000) { HiddenBoardLoading(rows = 8, cols = 6, maxSide = 600.dp) }
        composeRule.onNodeWithContentDescription("Loading board")
            .assertWidthIsEqualTo(450.dp)
            .assertHeightIsEqualTo(600.dp)
    }

    @Test
    fun `loading holds the board's slot as one block with one sheen`() {
        setAnimatorScale(1f)
        composeRule.mainClock.autoAdvance = false
        show(1000) { HiddenBoardLoading(rows = 8, cols = 6) }
        composeRule.onNodeWithContentDescription("Loading board")
            .assertWidthIsEqualTo(300.dp)
            .assertHeightIsEqualTo(400.dp)
        assertEquals(1, composeRule.onAllNodesWithTag(SHIMMER_TAG).fetchSemanticsNodes().size)
    }

    @Test
    fun `loading is square by default and still under reduced motion`() {
        setAnimatorScale(0f)
        show(360) { HiddenBoardLoading() }
        composeRule.onNodeWithContentDescription("Loading board")
            .assertWidthIsEqualTo(360.dp)
            .assertHeightIsEqualTo(360.dp)
        assertEquals(0, composeRule.onAllNodesWithTag(SHIMMER_TAG).fetchSemanticsNodes().size)
    }

    /** The board on a page taller than the screen, as a puzzle's page holds it. */
    private fun showOnAPage(sharp: Boolean): ScrollState {
        val scroll = ScrollState(0)
        show(360) {
            Column(Modifier.verticalScroll(scroll)) {
                HiddenBoard(leakyBoard(), sharp = sharp)
                Spacer(Modifier.height(2000.dp))
            }
        }
        return scroll
    }

    private fun assertNothingReachedTheBoard() {
        assertEquals(0, taps)
        assertEquals(0, presses)
        assertEquals(0, touches)
    }

    @Test
    fun `blurred, a swipe that starts on the board scrolls the page and reaches no cell`() {
        val scroll = showOnAPage(sharp = false)
        composeRule.onNodeWithContentDescription("Board hidden until you play").performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        assertTrue("the page scrolled, it is at ${scroll.value}", scroll.value > 0)
        assertNothingReachedTheBoard()
    }

    @Test
    fun `sharp, a swipe that starts on the board scrolls the page and reaches no cell`() {
        val scroll = showOnAPage(sharp = true)
        composeRule.onNodeWithContentDescription("Board").performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        assertTrue("the page scrolled, it is at ${scroll.value}", scroll.value > 0)
        assertNothingReachedTheBoard()
    }

    @Test
    fun `on a page that scrolls, a tap and a long press reach no cell and move nothing`() {
        val scroll = showOnAPage(sharp = false)
        composeRule.onNodeWithContentDescription("Board hidden until you play").performTouchInput {
            click()
            click(topLeft)
            longClick()
        }
        composeRule.waitForIdle()
        assertEquals(0, scroll.value)
        assertNothingReachedTheBoard()
    }

    private companion object {
        const val ROLE_STAND_IN = 1_000_000_000
        const val DESCRIPTION_STAND_IN = 2_000_000_000
    }
}
