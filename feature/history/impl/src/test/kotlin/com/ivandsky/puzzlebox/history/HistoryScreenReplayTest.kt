package com.ivandsky.puzzlebox.history

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.history.HistoryTestSupport.ZIP
import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Ticket 67: the screen tells its host which day's detail is open, so the replays load and drop with it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HistoryScreenReplayTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val day: LocalDate = HistoryTestSupport.TODAY
    private val row = HistoryDayRow(
        day = day,
        puzzleNumber = HistoryTestSupport.PUZZLE_NUMBER,
        gameId = ZIP,
        gameName = "Zip",
        record = HistoryTestSupport.record(day),
        currentYear = day.year,
    )
    private val reported = mutableListOf<HistoryDayRow?>()

    @Test
    fun `opening a day reports it and closing the detail reports null`() {
        val rows = listOf(row)
        val state = HistoryUiState(
            rows = rows,
            calendarDay = day,
            earliestDay = day,
            solvedByDay = solvedByDay(rows, listOf(ZIP)),
            daysWithRows = setOf(day),
            gamesPerDay = 1,
            today = day,
        )
        composeRule.setContent {
            PuzzleBoxTheme {
                HistoryScreen(
                    layout = HistoryLayout.SinglePane,
                    onBack = null,
                    days = flowOf(PagingData.from(rows)).collectAsLazyPagingItems(),
                    state = state,
                    onShowCalendar = { _, _ -> },
                    onSelectDay = {},
                    onPlayAgain = { _, _ -> },
                    replayHost = HistoryReplayHost(
                        replays = HistoryReplayHost.None.replays,
                        playUi = { null },
                        onOpenDay = { reported += it },
                        onOpen = { _, _ -> },
                    ),
                )
            }
        }
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithTag("history.list").performScrollToNode(hasTestTag("history.row.zip/$day")) }.isSuccess
        }
        reported.clear()

        composeRule.onNodeWithTag("history.row.zip/$day").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf<HistoryDayRow?>(row), reported)

        composeRule.onNodeWithContentDescription("Back to days").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(row, null), reported)
    }
}
