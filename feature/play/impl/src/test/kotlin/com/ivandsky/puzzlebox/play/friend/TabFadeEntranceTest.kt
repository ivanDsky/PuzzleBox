package com.ivandsky.puzzlebox.play.friend

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.UnderlineTab
import com.ivandsky.puzzlebox.designsystem.UnderlineTabPager
import com.ivandsky.puzzlebox.model.ShareCode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A tab that fades in is its own entrance: the cards on it start settled, whether the tab was tapped or swiped to. The
 * first open of the screen keeps the entrance of the tab it opens on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class TabFadeEntranceTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val codes = listOf(requireNotNull(ShareCode.parse("K7Q2MX")), requireNotNull(ShareCode.parse("B4N8TW")))
    private val animates = mutableMapOf<Int, Boolean>()

    @Composable
    private fun Page(page: Int) {
        animates[page] = rememberCardEntrance(codes[page]).animates
        Box(Modifier.fillMaxSize().testTag("page.$page"))
    }

    private fun show(initialPage: Int = 0) {
        composeRule.setContent {
            PuzzleBoxTheme {
                Box(Modifier.size(width = 360.dp, height = 400.dp)) {
                    UnderlineTabPager(
                        tabs = listOf(UnderlineTab("One", "t.one"), UnderlineTab("Two", "t.two")),
                        onReselect = {},
                        initialPage = initialPage,
                    ) { Page(it) }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `the tab the screen opens on keeps its entrance`() {
        show()

        assertEquals(true, animates[0])
    }

    @Test
    fun `a tab reached by a tap starts settled`() {
        show()

        composeRule.onNodeWithTag("t.two").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("page.1").assertExists()
        assertEquals(false, animates[1])
    }

    @Test
    fun `a tab reached by a swipe starts settled`() {
        show()

        composeRule.onNodeWithTag("underline.pager").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("page.1").assertExists()
        assertEquals(false, animates[1])
    }
}
