package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.history.CalendarState
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.resultui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.resultui.board.DailyBoardState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Ticket 1509: a scrolled Ranks pane stays where the finger left it while its data arrives again and while a pick in the
 * pane beside it changes what the screen shows. Each pane's own scroll state is the one thing that must outlive both.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class RanksScrollKeepsPositionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val places = listOf(
        BoardPlace(RanksBoard.Overall, "Overall", Standing.Place(4)),
        BoardPlace(RanksBoard.Game(GameId("zip")), "Zip", Standing.Place(2)),
        BoardPlace(RanksBoard.Game(GameId("tango")), "Tango", Standing.None),
    )

    private fun rows(count: Int, timeBase: Long) = List(count) { index ->
        DailyBoardRowModel(index + 1, "Player $index", timeBase + index * 1_000L, 0, isSelf = index == 3, playerId = "p$index")
    }

    private fun slice(rows: List<DailyBoardRowModel>) =
        RanksBoardSlice("Zip · today", RankOrder.TimeThenHints, DailyBoardState.Ranked(rows))

    private var state by mutableStateOf(RanksUiState(slice(rows(60, 60_000L)), boardCount = 3, places = places))

    /** How far this vertical scroller has moved, in px. */
    private fun SemanticsNodeInteraction.scrolled(): Float =
        fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun show(layout: HistoryLayout, height: Int) {
        composeRule.setContent {
            PuzzleBoxTheme {
                RanksScreen(
                    state,
                    RanksActions(
                        onSignIn = {},
                        onSwipe = {},
                        onPickBoard = { state = state.copy(position = places.indexOfFirst { place -> place.board == it }) },
                        onShowCalendar = { day, expanded -> state = state.copy(calendar = state.calendar.copy(shownDay = day, expanded = expanded)) },
                    ),
                    modifier = Modifier.height(height.dp),
                    layout = layout,
                    tuneDefaults = previewTuneDefaults(),
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `the phone's scrolled hub does not move when its board's rows arrive again`() {
        show(HistoryLayout.SinglePane, height = 300)
        composeRule.onNodeWithTag("route.ranks").performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        val before = composeRule.onNodeWithTag("route.ranks").scrolled()
        assertTrue("the hub scrolled", before > 0f)

        state = state.copy(board = slice(rows(60, 59_000L)))
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1_000)

        assertEquals(before, composeRule.onNodeWithTag("route.ranks").scrolled(), 0.5f)
    }

    @Test
    fun `the tablet's scrolled game list does not move on a pick or when the board arrives again`() {
        show(HistoryLayout.TwoPane, height = 260)
        composeRule.onNodeWithTag("ranks.leftpane").performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        val before = composeRule.onNodeWithTag("ranks.leftpane").scrolled()
        assertTrue("the left pane scrolled", before > 0f)

        composeRule.onNodeWithTag("ranks.list.item.2").performClick()
        composeRule.waitForIdle()
        state = state.copy(board = slice(rows(60, 59_000L)))
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1_000)

        assertEquals(before, composeRule.onNodeWithTag("ranks.leftpane").scrolled(), 0.5f)
    }

    @Test
    fun `the calendar's paged-back month does not return to this month when its days arrive again`() {
        val today = LocalDate.of(2026, 11, 13)
        state = state.copy(calendar = CalendarState(shownDay = today, expanded = true, earliestDay = today.minusDays(120), daysWithRows = setOf(today), gamesPerDay = 1, today = today))
        show(HistoryLayout.SinglePane, height = 600)
        composeRule.onNodeWithTag("history.calendar.prev", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("history.calendar.month", useUnmergedTree = true).assertTextEquals("October 2026")

        state = state.copy(calendar = state.calendar.copy(daysWithRows = setOf(today, today.minusDays(20)), earliestDay = today.minusDays(150)))
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1_000)

        composeRule.onNodeWithTag("history.calendar.month", useUnmergedTree = true).assertTextEquals("October 2026")
    }
}
