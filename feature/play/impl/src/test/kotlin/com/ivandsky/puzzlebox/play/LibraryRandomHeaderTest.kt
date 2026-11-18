package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinApplication
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Dailies header at a 360 dp phone: Random and Enter code both fit at touch size, and the subtitle stays whole. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class LibraryRandomHeaderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var koin: KoinApplication? = null
    private var randomGame: GameId? = GameId("unset")

    @Before
    fun startGraph() {
        koin = startGamePageRankKoin(previewState().rows.map { it.card.game })
    }

    @After
    fun stopGraph() {
        koin?.close()
    }

    private fun mount(onRandom: ((GameId?) -> Unit)?) {
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
                    onRandom = onRandom,
                    onMakeOne = {},
                )
            }
        }
    }

    @Test
    fun `both header buttons are at least 48 dp tall and inside the 360 dp screen`() {
        mount { randomGame = it }

        listOf("library.random", "library.enterCode").forEach { tag ->
            val node = composeRule.onNodeWithTag(tag)
            node.assertIsDisplayed()
            node.assertHeightIsAtLeast(48.dp)
            val right = node.getBoundsInRoot().right
            assertTrue("$tag ends at $right", right <= 360.dp)
        }
    }

    @Test
    fun `the whole subtitle shows in at most two lines beside the buttons`() {
        mount { randomGame = it }

        val subtitle = composeRule.onNodeWithText("Every puzzle, back to #1")
        subtitle.assertIsDisplayed()
        val height = subtitle.getBoundsInRoot().height
        assertTrue("subtitle is $height tall; two lines are 42 dp, three 63", height <= 44.dp)
    }

    @Test
    fun `Random reports the Games tab, a null game`() {
        mount { randomGame = it }

        composeRule.onNodeWithTag("library.random").performClick()

        assertEquals(null, randomGame)
    }

    @Test
    fun `without an onRandom the header has Enter code alone`() {
        mount(onRandom = null)

        composeRule.onNodeWithTag("library.random").assertDoesNotExist()
        composeRule.onNodeWithTag("library.enterCode").assertIsDisplayed()
    }
}
