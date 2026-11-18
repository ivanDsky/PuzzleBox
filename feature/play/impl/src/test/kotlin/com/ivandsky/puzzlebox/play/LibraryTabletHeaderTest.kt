package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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

/**
 * The Dailies header in the tablet's list pane, 400 dp wide after the screen's 24 dp start padding: the title stays one
 * line and the subtitle at most two beside Enter code, which ends inside the pane; there is no Random.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp")
class LibraryTabletHeaderTest {

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
                    layout = PlayLayout.TwoPane,
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
    fun `Enter code is touch size and ends inside the list pane, with no Random`() {
        mount()

        val node = composeRule.onNodeWithTag("library.enterCode")
        node.assertIsDisplayed()
        node.assertHeightIsAtLeast(48.dp)
        val right = node.getBoundsInRoot().right
        assertTrue("library.enterCode ends at $right", right <= 424.dp)
        composeRule.onNodeWithTag("library.random").assertDoesNotExist()
    }

    @Test
    fun `the title is one line and the subtitle at most two beside the button`() {
        mount()

        val title = composeRule.onAllNodesWithText("Games")[0]
        title.assertIsDisplayed()
        val titleHeight = title.getBoundsInRoot().height
        assertTrue("title is $titleHeight tall; one line is 45 dp, two 90", titleHeight <= 50.dp)
        val subtitle = composeRule.onNodeWithText("Every puzzle, back to #1")
        subtitle.assertIsDisplayed()
        val subtitleHeight = subtitle.getBoundsInRoot().height
        assertTrue("subtitle is $subtitleHeight tall; two lines are 42 dp, three 63", subtitleHeight <= 44.dp)
    }
}
