package com.ivandsky.puzzlebox.home

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasText
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

/** The recap card on Home's Today list (A2, A3): present only with a slot, opens on a tap, dismisses on its close button. */
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
    }

    @Test
    fun `a slot shows the card with the period's label`() {
        show(RecapSlot("October 2026", onOpen = {}, onDismiss = {}))

        composeRule.onNodeWithTag("summary.recap").performScrollTo().assertHasClickAction()
        composeRule.onNodeWithTag("summary.recap").assert(hasText("October 2026", substring = true))
    }

    @Test
    fun `tapping the card opens the recap and not the dismissal`() {
        var opened = 0
        var dismissed = 0
        show(RecapSlot("October 2026", onOpen = { opened++ }, onDismiss = { dismissed++ }))

        composeRule.onNodeWithTag("summary.recap").performScrollTo().performClick()

        assertEquals(1 to 0, opened to dismissed)
    }

    @Test
    fun `the close button is named for its period and dismisses without opening`() {
        var opened = 0
        var dismissed = 0
        show(RecapSlot("October 2026", onOpen = { opened++ }, onDismiss = { dismissed++ }))

        composeRule.onNodeWithContentDescription("Dismiss October 2026 recap").performScrollTo().performClick()

        assertEquals(0 to 1, opened to dismissed)
    }
}
