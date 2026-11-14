package com.ivandsky.puzzlebox.home

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.StreakFreezes
import com.ivandsky.puzzlebox.game.RankOrder
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The approved Home pane freeze count: it sits on the "days in a row" title line (SC11, F2) and shows for a live
 * streak even with 0 freezes held, but never at streak 0 (SC12, F5). Reads rendered nodes, so it holds however the
 * streak and the count are derived.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class HomeStreakPaneFreezeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the freeze count's vertical centre lies within the days in a row title`() {
        render(streak = 12, held = 3)
        composeRule.onNodeWithContentDescription("3 freezes").assertIsDisplayed()

        val title = composeRule.onNodeWithText("days in a row").fetchSemanticsNode().boundsInRoot
        val count = composeRule.onNodeWithContentDescription("3 freezes").fetchSemanticsNode().boundsInRoot
        val centre = (count.top + count.bottom) / 2f
        assertTrue("count centre $centre outside title ${title.top}..${title.bottom}", centre in title.top..title.bottom)
    }

    @Test
    fun `a live streak with no freeze held still shows the 0 count`() {
        render(streak = 12, held = 0)
        composeRule.onNodeWithContentDescription("0 freezes").assertIsDisplayed()
    }

    @Test
    fun `streak 0 shows no count even with freezes held`() {
        render(streak = 0, held = 3)
        composeRule.onNodeWithText("days in a row").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("freeze", substring = true).assertCountEquals(0)
    }

    private fun render(streak: Int, held: Int) {
        val freezes = StreakFreezes(held = held, frozenDays = emptySet(), daysToNext = 14, lastSolve = null)
        val stats = GameStats(streak, 21, 1_234, 980, 96_000L, LocalDate.of(2025, 3, 14), best = null, rankOrder = RankOrder.TimeThenHints, freezes = freezes)
        val state = HomeUiState(
            today = emptyList(),
            identity = null,
            history = emptyList(),
            puzzleDay = DAY,
            playTarget = null,
            stats = stats,
            week = emptyList(),
            rolloverAt = Instant.EPOCH,
        )
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { PuzzleBoxTheme { HomeStreakPane(state = state, modifier = Modifier.width(768.dp)) } }
        // Past the pane's entrance slide.
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.waitForIdle()
    }

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 11, 5)
        const val SETTLE_MS = 2_000L
    }
}
