package com.ivandsky.puzzlebox.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.height
import com.ivandsky.puzzlebox.designsystem.LocalTabReselect
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.home.HomeScreenTest.Companion.TODAY
import com.ivandsky.puzzlebox.home.HomeScreenTest.Companion.state
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Home's Recent section (A11, A12): the history rows, the picked day that takes over the stats pane
 * and the tap that clears it. Everything else on Home stays in [HomeScreenTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeScreenRecentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(layout: HomeLayout, history: List<HistoryDay> = emptyList(), loaded: Boolean = true) {
        composeRule.setContent {
            CompositionLocalProvider(LocalTabReselect provides MutableSharedFlow<Unit>()) {
                PuzzleBoxTheme {
                    var picked by remember { mutableStateOf<String?>(null) }
                    HomeScreen(
                        layout = layout,
                        state = state(TODAY, history, GameId("tango")).copy(selectedDay = picked, isLocalStateLoaded = loaded),
                        onOpenGame = {},
                        onSelectDay = { picked = it },
                        modifier = Modifier,
                        title = "PuzzleBox",
                        summaries = HomeSummaries(onOpenRank = null, duels = null, onOpenDay = {}, onOpenStreak = {}),
                    )
                }
            }
        }
    }

    @Test
    fun `tapping a recent day swaps the pane to that day and away from the stats`() {
        setContent(
            HomeLayout.TwoPane,
            history = listOf(HistoryDay("2026-11-03", "November 3", listOf(solvedZip()))),
        )

        composeRule.onNodeWithTag("home.history_row_2026-11-03").performScrollTo().performClick()

        composeRule.onNodeWithTag("home.day_detail").assertExists()
        composeRule.onNodeWithTag("home.streak_pane").assertDoesNotExist()
    }

    @Test
    fun `tapping the selected recent row again clears the pick and restores the streak pane`() {
        setContent(
            HomeLayout.TwoPane,
            history = listOf(HistoryDay("2026-11-03", "November 3", listOf(solvedZip()))),
        )
        composeRule.onNodeWithTag("home.history_row_2026-11-03").performScrollTo().performClick()
        composeRule.onNodeWithTag("home.day_detail").assertExists()

        composeRule.onNodeWithTag("home.history_row_2026-11-03").performScrollTo().performClick()

        composeRule.onNodeWithTag("home.streak_pane").assertExists()
        composeRule.onNodeWithTag("home.day_detail").assertDoesNotExist()
        composeRule.onNodeWithTag("home.history_row_2026-11-03").assertIsNotSelected()
    }

    @Test
    fun `history teaser shows one line when nothing is cached`() {
        setContent(HomeLayout.SinglePane)

        composeRule.onAllNodesWithText("Finished puzzles show here").assertCountEquals(1)
        composeRule.onNodeWithText("See all").assertDoesNotExist()
    }

    @Test
    fun `history rows render a formatted headline per day`() {
        setContent(
            HomeLayout.TwoPane,
            history = listOf(HistoryDay("2026-10-30", "October 30", listOf(solvedZip()))),
        )

        composeRule.onNodeWithText("October 30").assertExists()
        composeRule.onNodeWithText("Finished puzzles show here").assertDoesNotExist()
    }

    @Test
    fun `before local state loads the recent card stays blank with no empty claim`() {
        setContent(HomeLayout.SinglePane, loaded = false)

        composeRule.onAllNodesWithText("Finished puzzles show here").assertCountEquals(0)
    }

    @Test
    fun `recent card keeps its height when local state finishes loading`() {
        var loaded by mutableStateOf(false)
        composeRule.setContent {
            CompositionLocalProvider(LocalTabReselect provides MutableSharedFlow<Unit>()) {
                PuzzleBoxTheme {
                    HomeScreen(
                        layout = HomeLayout.SinglePane,
                        state = state(TODAY, emptyList(), GameId("tango")).copy(isLocalStateLoaded = loaded),
                        onOpenGame = {},
                        onSelectDay = {},
                        modifier = Modifier,
                        title = "PuzzleBox",
                        summaries = HomeSummaries(onOpenRank = null, duels = null, onOpenDay = {}, onOpenStreak = {}),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val blankHeight = recentCardHeight()
        loaded = true
        composeRule.waitForIdle()
        assertEquals(blankHeight, recentCardHeight(), 0.5f)
    }

    private fun recentCardHeight(): Float =
        composeRule.onNodeWithTag("home.recent_card", useUnmergedTree = true).getUnclippedBoundsInRoot().height.value

    @Test
    fun `selecting a two-game day on two pane selects exactly that row`() {
        val twoGames = listOf(solvedZip(), openedTango())
        setContent(
            HomeLayout.TwoPane,
            history = listOf(
                HistoryDay("2026-11-03", "November 3", twoGames),
                HistoryDay("2026-11-02", "November 2", twoGames.take(1)),
                HistoryDay("2026-11-01", "November 1", twoGames.takeLast(1)),
            ),
        )

        composeRule.onNodeWithTag("home.history_row_2026-11-03").performScrollTo().performClick()

        composeRule.onAllNodes(isSelected()).assertCountEquals(1)
        composeRule.onNodeWithTag("home.history_row_2026-11-03").assertIsSelected()
    }

    @Test
    fun `phone keeps recent days beside today and the streak`() {
        val day = HistoryDay("2026-11-03", "November 3", listOf(solvedZip()))
        setContent(HomeLayout.SinglePane, history = listOf(day))

        composeRule.onNodeWithTag("home.history_row_2026-11-03").assertExists()
        composeRule.onNodeWithTag("home.today").assertExists()
        composeRule.onNodeWithTag("home.streak").assertExists()
    }

    @Test
    fun `tablet shows a tapped day's detail`() {
        val day = HistoryDay("2026-11-03", "November 3", listOf(solvedZip()))
        setContent(HomeLayout.TwoPane, history = listOf(day))

        composeRule.onNodeWithTag("home.streak_pane").assertExists()
        composeRule.onNodeWithTag("home.history_row_2026-11-03").performScrollTo().performClick()
        composeRule.onNodeWithTag("home.day_detail").assertExists()
    }
}
