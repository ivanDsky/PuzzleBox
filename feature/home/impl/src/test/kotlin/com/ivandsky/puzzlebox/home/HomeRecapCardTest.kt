package com.ivandsky.puzzlebox.home

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import com.ivandsky.puzzlebox.designsystem.LocalTabReselect
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The recap tile on Home's Today list (A2, A3): present only with a slot, new or watched, opens on a tap, dismisses on its ×. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeRecapCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(recap: RecapSlot?) {
        composeRule.setContent {
            CompositionLocalProvider(LocalTabReselect provides MutableSharedFlow()) {
                PuzzleBoxTheme {
                    HomeScreen(
                        layout = HomeLayout.SinglePane,
                        state = HomeScreenTest.state(HomeScreenTest.TODAY, emptyList(), GameId("tango")),
                        onOpenGame = {},
                        onSelectDay = {},
                        modifier = Modifier,
                        title = "PuzzleBox",
                        summaries = HomeSummaries(null, null, {}, {}, recap = recap),
                    )
                }
            }
        }
    }

    @Test
    fun `no slot shows no card`() {
        show(recap = null)

        composeRule.onNodeWithTag("summary.recap").assertDoesNotExist()
        composeRule.onNodeWithTag("summary.recap.dismiss").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Loading your recap").assertDoesNotExist()
    }

    @Test
    fun `a slot shows the tile, read as one button with its period and count`() {
        show(previewRecap())

        composeRule.onNodeWithTag("summary.recap").performScrollTo().assertHasClickAction()
            .assert(hasContentDescription("Open your October 2026 recap. 96 puzzles solved."))
        composeRule.onNodeWithText("Your October 2026 recap", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("See how the month went", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `an unopened recap says New and a watched one says Watched, never both`() {
        show(previewRecap(opened = false))
        composeRule.onNodeWithText("NEW", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("WATCHED", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `a watched recap drops New`() {
        show(previewRecap(opened = true))
        composeRule.onNodeWithText("WATCHED", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Watch it again", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("NEW", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `a year says how the year went`() {
        show(previewRecap(label = "2026", periodId = "2026", yearly = true))

        composeRule.onNodeWithText("See how the year went", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("1,240", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `tapping the tile opens the recap and not the dismissal`() {
        var opened = 0
        var dismissed = 0
        show(previewRecap(onOpen = { opened++ }, onDismiss = { dismissed++ }))

        composeRule.onNodeWithTag("summary.recap").performScrollTo().performClick()

        assertEquals(1 to 0, opened to dismissed)
    }

    @Test
    fun `the close button is named for its period and dismisses without opening`() {
        var opened = 0
        var dismissed = 0
        show(previewRecap(onOpen = { opened++ }, onDismiss = { dismissed++ }))

        composeRule.onNodeWithContentDescription("Dismiss October 2026 recap").performScrollTo().performClick()

        assertEquals(0 to 1, opened to dismissed)
    }

    @Test
    fun `while a due recap loads the skeleton is read as loading and is no tile`() {
        composeRule.mainClock.autoAdvance = false
        show(RecapSlot.Loading)
        composeRule.mainClock.advanceTimeBy(1_000L)

        composeRule.onNodeWithContentDescription("Loading your recap").assertExists()
        composeRule.onNodeWithTag("summary.recap").assertDoesNotExist()
    }
}
