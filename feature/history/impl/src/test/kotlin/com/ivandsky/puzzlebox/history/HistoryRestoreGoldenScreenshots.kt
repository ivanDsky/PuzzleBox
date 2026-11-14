package com.ivandsky.puzzlebox.history

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * History after the first cloud restore (ticket 66), on the golden phone, light and dark, as settled frames: the
 * restoring card, and the rows it gives way to once the restore ends with items. Goldens in `snapshots/restore/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class HistoryRestoreGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val idle = LoadStates(LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true))
    private val pages = MutableStateFlow(PagingData.empty<HistoryDayRow>(idle))
    private var state by mutableStateOf(HistoryUiState(today = DAY, restoring = true))
    private lateinit var items: LazyPagingItems<HistoryDayRow>

    @Test fun `restoring light`() = restoring(dark = false)
    @Test fun `restoring dark`() = restoring(dark = true)

    @Test fun `restored light`() = restored(dark = false)
    @Test fun `restored dark`() = restored(dark = true)

    private fun show(dark: Boolean) {
        rule.showGolden(dark) {
            items = pages.collectAsLazyPagingItems()
            val scheme = MaterialTheme.colorScheme
            // The screen's own pairing: cards float lighter than the pane in both themes.
            val cardColor = if (scheme.surface.luminance() > scheme.surfaceContainerHighest.luminance()) scheme.surface else scheme.surfaceContainerLow
            ListPane(
                days = items,
                state = state,
                onShowCalendar = { _, _ -> },
                onSelectDay = {},
                selectedKey = null,
                onDayClick = {},
                onBack = null,
                cardColor = cardColor,
            )
        }
    }

    private fun restoring(dark: Boolean) {
        show(dark)
        rule.settleAndCapture("restore", "history-restoring", "phone", dark)
    }

    // The restore ends with rows: the pages land (they load off the main thread, so wait for them), then the frame settles.
    private fun restored(dark: Boolean) {
        show(dark)
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        val rows = (0 until 3).map { index ->
            HistoryDayRow(day = DAY.minusDays(index.toLong()), puzzleNumber = 7 - index, gameId = HistoryTestSupport.ZIP, gameName = "Zip")
        }
        rule.runOnUiThread {
            state = state.copy(restoring = false)
            pages.value = PagingData.from(rows, idle)
        }
        val deadline = System.nanoTime() + 10_000_000_000L
        while (System.nanoTime() < deadline) {
            rule.waitForIdle()
            var arrived = false
            rule.runOnUiThread { arrived = items.itemCount > 0 }
            if (arrived) break
            Thread.sleep(10)
        }
        rule.settleAndCapture("restore", "history-restored", "phone", dark)
    }

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 11, 3)
        const val SETTLE_MS = 2_000L
    }
}
