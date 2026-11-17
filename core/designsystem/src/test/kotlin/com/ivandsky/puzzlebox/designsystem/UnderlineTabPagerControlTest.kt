package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// nav-rethink M12-6 (ticket 125): a host that owns the tab (Games) hears the settled page and can move the pager.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class UnderlineTabPagerControlTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val three = listOf(
        UnderlineTab("New today", "t.new"),
        UnderlineTab("Friends", "t.friends"),
        UnderlineTab("Yours", "t.yours"),
    )

    private val settled = mutableListOf<Int>()
    private var requested by mutableStateOf<Int?>(null)

    private fun show() {
        composeRule.setContent {
            PuzzleBoxTheme {
                Box(Modifier.size(width = 360.dp, height = 400.dp)) {
                    UnderlineTabPager(
                        three,
                        onReselect = {},
                        selectedPage = requested,
                        onPageSettled = { settled += it },
                    ) { Box(Modifier.fillMaxSize().testTag("page.$it")) }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `a tap on another tab reports the page it settled on, once`() {
        show()

        composeRule.onNodeWithTag("t.friends").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(1), settled)
    }

    @Test
    fun `a swipe reports the page it settled on`() {
        show()

        composeRule.onNodeWithTag("underline.pager").performTouchInput {
            down(center)
            moveBy(Offset(-width * 0.7f, 0f), delayMillis = 16)
            up()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("t.friends").assertIsSelected()
        assertEquals(listOf(1), settled)
    }

    @Test
    fun `nothing is reported at rest`() {
        show()

        assertEquals(emptyList<Int>(), settled)
    }

    @Test
    fun `a new selected page moves the pager there`() {
        show()

        requested = 2
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("t.yours").assertIsSelected()
        composeRule.onNodeWithTag("page.2").assertExists()
        assertEquals(listOf(2), settled)
    }

    @Test
    fun `the page the pager already shows is left alone`() {
        show()
        composeRule.onNodeWithTag("t.friends").performClick()
        composeRule.waitForIdle()

        requested = 1
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("t.friends").assertIsSelected()
        assertEquals(listOf(1), settled)
    }
}
