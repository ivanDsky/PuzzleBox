package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.PlayLayout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinApplication
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Dailies header at a 360 dp phone: Enter code fits at touch size with no Random beside it, and the subtitle stays whole. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class LibraryHeaderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var koin: KoinApplication? = null

    @Before
    fun startGraph() {
        koin = startGamePageRankKoin(previewState().rows.map { it.card.game })
    }

    @After
    fun stopGraph() {
        koin?.close()
    }

    private fun mount() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                LibraryScreen(
                    state = previewState(),
                    page = null,
                    month = null,
                    layout = PlayLayout.SinglePane,
                    hasLesson = { false },
                    onSelect = {},
                    onPlay = { _, _ -> },
                    onRetry = {},
                    onOpenLesson = {},
                    calendar = CalendarActions(),
                    onOpenRanks = { _, _ -> },
                    onEnterCode = {},
                    onRandom = {},
                    onMakeOne = {},
                )
            }
        }
    }

    @Test
    fun `Enter code is at least 48 dp tall and inside the 360 dp screen`() {
        mount()

        val node = composeRule.onNodeWithTag("library.enterCode")
        node.assertIsDisplayed()
        node.assertHeightIsAtLeast(48.dp)
        val right = node.getBoundsInRoot().right
        assertTrue("library.enterCode ends at $right", right <= 360.dp)
    }

    @Test
    fun `the whole subtitle shows in at most two lines beside the button`() {
        mount()

        val subtitle = composeRule.onNodeWithText("Every puzzle, back to #1")
        subtitle.assertIsDisplayed()
        val height = subtitle.getBoundsInRoot().height
        assertTrue("subtitle is $height tall; two lines are 42 dp, three 63", height <= 44.dp)
    }

    @Test
    fun `the header has no Random even with a Random handler`() {
        mount()

        composeRule.onNodeWithTag("library.random").assertDoesNotExist()
        composeRule.onNodeWithTag("library.enterCode").assertIsDisplayed()
    }
}
