package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinApplication
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Games screen hands its Make one handler to the opened game page, and only when it has one. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class LibraryMakeOneTest {

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

    private fun openZipPage(onMakeOne: ((GameId) -> Unit)?) {
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
                    onMakeOne = onMakeOne,
                )
            }
        }
        composeRule.onNodeWithTag("library.row.zip").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `the opened page's Make one reports the game`() {
        val made = mutableListOf<GameId>()
        openZipPage { made += it }
        composeRule.onNodeWithTag("library.page.makeOne").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(listOf(GameId("zip")), made) }
    }

    @Test
    fun `without a handler the opened page has no Make one`() {
        openZipPage(onMakeOne = null)
        composeRule.onNodeWithTag("library.page.zip").assertExists()
        composeRule.onNodeWithTag("library.page.makeOne").assertDoesNotExist()
    }
}
