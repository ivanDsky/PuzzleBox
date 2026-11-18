package com.ivandsky.puzzlebox.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.designsystem.LocalTabReselect
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.home.HomeScreenTest.Companion.TODAY
import com.ivandsky.puzzlebox.home.HomeScreenTest.Companion.state
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Ticket 1509: Home's scrolled list keeps its place when its state arrives again and when a recent day picked in it
 * swaps the pane beside it, on the tablet and on the phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h400dp")
class HomeScrollKeepsPositionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var history by mutableStateOf(days("12:00"))

    private fun days(time: String) = List(DAYS) { index ->
        HistoryDay("2026-10-%02d".format(index + 1), "October ${index + 1} $time", listOf(solvedZip()))
    }

    private fun show(layout: HomeLayout) {
        composeRule.setContent {
            CompositionLocalProvider(LocalTabReselect provides MutableSharedFlow<Unit>()) {
                PuzzleBoxTheme {
                    var picked by remember { mutableStateOf<String?>(null) }
                    HomeScreen(
                        layout = layout,
                        state = state(TODAY, history, GameId("tango")).copy(selectedDay = picked),
                        onOpenGame = {},
                        onSelectDay = { picked = it },
                        modifier = Modifier,
                        title = "PuzzleBox",
                        summaries = HomeSummaries(onOpenRank = null, duels = null, onOpenDay = {}, onOpenStreak = {}),
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** The list pane's scroller: the vertical one that holds the recent rows. */
    private fun list() = composeRule.onNode(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and hasAnyDescendant(hasTestTag(LAST_ROW)),
    )

    private fun SemanticsNodeInteraction.scrolled(): Float =
        fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test
    fun `the tablet's scrolled Home list stays put when a recent day swaps the pane and the history arrives again`() {
        show(HomeLayout.TwoPane)
        composeRule.onNodeWithTag(LAST_ROW).performScrollTo()
        composeRule.waitForIdle()
        val before = list().scrolled()
        assertTrue("the Home list scrolled", before > 0f)

        composeRule.onNodeWithTag(LAST_ROW).performClick()
        composeRule.waitForIdle()
        history = days("12:01")
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1_000)

        assertEquals(before, list().scrolled(), 0.5f)
    }

    @Test
    fun `the phone's scrolled Home list stays put when the history arrives again`() {
        show(HomeLayout.SinglePane)
        composeRule.onNodeWithTag(LAST_ROW).performScrollTo()
        composeRule.waitForIdle()
        val before = list().scrolled()
        assertTrue("the Home list scrolled", before > 0f)

        history = days("12:01")
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1_000)

        assertEquals(before, list().scrolled(), 0.5f)
    }

    private companion object {
        const val DAYS = 12
        const val LAST_ROW = "home.history_row_2026-10-12"
    }
}
