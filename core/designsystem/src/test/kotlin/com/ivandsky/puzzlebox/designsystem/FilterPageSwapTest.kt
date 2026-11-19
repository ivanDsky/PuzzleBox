package com.ivandsky.puzzlebox.designsystem

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

private enum class TestPage { Game }

/**
 * The swap host (epic picker-filters, ticket 11): content and page swap in place, the content keeps its state, a rotation keeps the page,
 * Back inside a real AppModalBottomSheet, and the Back arrow's focus. The fade is in [FilterPageSwapMotionTest].
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class FilterPageSwapTest {

    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun resetScale() = animatorScale(1f)

    private fun animatorScale(scale: Float) {
        Settings.Global.putFloat(ApplicationProvider.getApplicationContext<Context>().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    private val picked = mutableStateOf<String?>(null)
    private var dismissals = 0
    private var scrolled = -1
    private var inputMode: InputModeManager? = null

    private val options = listOf(FilterOption("a", "Alpha", tag = "opt.a"), FilterOption("b", "Beta", tag = "opt.b"))

    /** A host as the surface tickets write it: its page is saved, its chip-like button opens the page, and its content holds state. */
    @Composable
    private fun Host(inSheet: Boolean) {
        var page by rememberSaveable { mutableStateOf<TestPage?>(null) }
        inputMode = LocalInputModeManager.current
        val swap = @Composable {
            FilterPageSwap(
                page = page,
                onBack = { page = null },
                pageContent = {
                    SingleFilterPage("Game", "All games", options, picked.value, onSelect = { picked.value = it }, onBack = { page = null }, tag = "x.filter.game", allTag = "x.filter.game.all")
                },
            ) {
                var count by rememberSaveable { mutableIntStateOf(0) }
                var text by rememberSaveable { mutableStateOf("") }
                val scroll = rememberScrollState()
                scrolled = scroll.value
                Column(Modifier.fillMaxWidth()) {
                    Text("count $count", Modifier.testTag("count"))
                    Box(Modifier.size(48.dp).testTag("inc").clickable { count++ })
                    BasicTextField(text, { text = it }, Modifier.testTag("field"))
                    Box(Modifier.size(48.dp).testTag("open").clickable { page = TestPage.Game })
                    Column(Modifier.height(100.dp).verticalScroll(scroll).testTag("scroller")) {
                        repeat(30) { Text("line $it", Modifier.height(40.dp).testTag("line.$it")) }
                    }
                }
            }
        }
        PuzzleBoxTheme(darkTheme = false) {
            if (inSheet) {
                AppModalBottomSheet(onDismissRequest = { dismissals++ }) { swap() }
            } else {
                Box(Modifier.background(Color.White)) { swap() }
            }
        }
    }

    private fun show(inSheet: Boolean = false, restoration: StateRestorationTester? = null) {
        val content: @Composable () -> Unit = { Host(inSheet) }
        if (restoration != null) restoration.setContent(content) else composeRule.setContent(content)
        settle()
    }

    private fun settle() {
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        advance(SETTLE_MS)
    }

    private fun advance(ms: Long) = composeRule.mainClock.advanceTimeBy(ms)

    private fun node(tag: String) = composeRule.onNodeWithTag(tag)

    private fun count(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun open() {
        node("open").performClick()
        advance(SETTLE_MS)
    }

    private fun backArrow() {
        node("x.filter.game.page.back").performClick()
        advance(SETTLE_MS)
    }

    private fun systemBack() {
        val dialog = ShadowDialog.getLatestDialog()
        composeRule.runOnUiThread { dialog.onBackPressed() }
        composeRule.waitForIdle()
        advance(SETTLE_MS)
    }

    @Test
    fun `with no page only the content shows, a page replaces it and none brings it back`() {
        show()
        assertEquals(1, count("open"))
        assertEquals(0, count("x.filter.game.page"))

        open()
        assertEquals("the content leaves the tree", 0, count("open") + count("count") + count("field"))
        assertEquals(1, count("x.filter.game.page"))

        backArrow()
        assertEquals(1, count("open"))
        assertEquals(0, count("x.filter.game.page"))
    }

    @Test
    fun `a tap on an option returns to the content with the pick reported`() {
        show()
        open()

        node("opt.b").performClick()
        advance(SETTLE_MS)

        assertEquals("b", picked.value)
        assertEquals(1, count("open"))
        assertEquals(0, count("x.filter.game.page"))
    }

    @Test
    fun `the content keeps its counter, its typed text and its scroll offset across a swap`() {
        show()
        node("inc").performClick()
        node("inc").performClick()
        node("field").performTextInput("hello")
        node("scroller").performTouchInput { swipeUp() }
        advance(SETTLE_MS)
        val offset = scrolled
        assertTrue("scrolled: $offset", offset > 0)

        open()
        backArrow()

        node("count").assertTextEquals("count 2")
        node("field").assertTextEquals("hello")
        assertEquals(offset, scrolled)
    }

    @Test
    fun `a rotation with a page open comes back on the page and Back then returns to the content with its state`() {
        val restoration = StateRestorationTester(composeRule)
        show(restoration = restoration)
        node("inc").performClick()
        node("field").performTextInput("kept")
        open()

        restoration.emulateSavedInstanceStateRestore()
        advance(SETTLE_MS)
        assertEquals(1, count("x.filter.game.page"))
        assertEquals(0, count("open"))

        backArrow()
        node("count").assertTextEquals("count 1")
        node("field").assertTextEquals("kept")
    }

    @Test
    fun `system back with a page open returns to the content and leaves the sheet up`() {
        show(inSheet = true)
        open()

        systemBack()

        assertEquals("the sheet did not dismiss", 0, dismissals)
        assertEquals(1, count("open"))
        assertEquals(0, count("x.filter.game.page"))
    }

    @Test
    fun `the Back arrow in a sheet returns to the content and leaves the sheet up`() {
        show(inSheet = true)
        open()

        backArrow()

        assertEquals(0, dismissals)
        assertEquals(1, count("open"))
    }

    @Test
    fun `system back with no page dismisses the sheet as before`() {
        show(inSheet = true)

        systemBack()

        assertEquals(1, dismissals)
    }

    @Test
    fun `the scrim dismisses the sheet from the page as from the content`() {
        show(inSheet = true)
        open()

        composeRule.onNodeWithContentDescription("Close sheet").performTouchInput { click(topLeft + Offset(4f, 4f)) }
        repeat(60) { advance(FRAME_MS) }

        assertEquals(1, dismissals)
    }

    @Test
    fun `the page's Back arrow has focus when it opens`() {
        show()
        // A button takes focus only outside touch mode, so this is the keyboard and D-pad path.
        composeRule.runOnUiThread { inputMode!!.requestInputMode(InputMode.Keyboard) }
        open()

        node("x.filter.game.page.back").assertIsFocused()
    }

    private companion object {
        const val SETTLE_MS = 1000L
        const val FRAME_MS = 16L
    }
}
