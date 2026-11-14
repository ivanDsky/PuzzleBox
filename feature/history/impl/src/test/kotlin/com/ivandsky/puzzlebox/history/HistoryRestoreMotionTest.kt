package com.ivandsky.puzzlebox.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What History's list does when the first cloud restore ends with rows: the restored rows are not yet at full
 * strength a frame after they arrive (they fade in with the list's insert motion) and are once it has played out, and
 * the restoring card is gone. A history that was never restoring changes nothing: its rows arrive at full strength at
 * once. Behavior only, on a paused clock; the fade's timing is the motion helper's.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HistoryRestoreMotionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val idle = LoadStates(LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true))
    private val pages = MutableStateFlow(PagingData.empty<HistoryDayRow>(idle))
    private var state by mutableStateOf(HistoryUiState(today = DAY))
    private lateinit var items: LazyPagingItems<HistoryDayRow>
    private val row = HistoryDayRow(day = DAY, puzzleNumber = 7, gameId = HistoryTestSupport.ZIP, gameName = "Zip")
    private val rowTag get() = "history.row.${row.key}"

    private fun show(restoring: Boolean) {
        state = HistoryUiState(today = DAY, restoring = restoring)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme {
                items = pages.collectAsLazyPagingItems()
                ListPane(
                    days = items,
                    state = state,
                    onShowCalendar = { _, _ -> },
                    onSelectDay = {},
                    selectedKey = null,
                    onDayClick = {},
                    onBack = null,
                    cardColor = Color.Red,
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
    }

    // The restore ends with the row: the test clock runs frames until the pages have landed (they take a frame, never real
    // time), then one more frame, so the row is a frame old.
    private fun restore() {
        composeRule.runOnUiThread {
            state = state.copy(restoring = false)
            pages.value = PagingData.from(listOf(row), idle)
        }
        var frames = 0
        while (items.itemCount == 0 && frames < MAX_FRAMES) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            frames++
        }
        assertTrue("the restored pages landed within $MAX_FRAMES frames", items.itemCount > 0)
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun exists(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    // The row card's colour in its empty middle, between the title and the pill.
    private fun rowPixel(): Int {
        val image = composeRule.onNodeWithTag(rowTag, useUnmergedTree = true).captureToImage().asAndroidBitmap()
        return image.getPixel(image.width / 2, image.height / 2)
    }

    @Test
    fun `restored rows fade in and the restoring card is gone`() {
        show(restoring = true)
        assertTrue("the restoring card is shown before the restore ends", exists("history.restoring"))

        restore()
        assertTrue("the restored row is on show", exists(rowTag))
        assertTrue("the restoring card is gone", !exists("history.restoring"))
        val arriving = rowPixel()
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)

        assertNotEquals("a frame after arriving the row is still fading in", rowPixel(), arriving)
    }

    @Test
    fun `a history that was never restoring shows its rows at full strength at once`() {
        show(restoring = false)
        assertTrue("the empty card is shown", exists("history.empty"))

        restore()
        assertTrue("the row is on show", exists(rowTag))
        assertTrue("the empty card leaves at once, as before", !exists("history.empty"))
        val arriving = rowPixel()
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)

        assertEquals("the row did not fade in", rowPixel(), arriving)
    }

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 11, 3)
        const val SETTLE_MS = 2_000L
        const val MAX_FRAMES = 60
    }
}
