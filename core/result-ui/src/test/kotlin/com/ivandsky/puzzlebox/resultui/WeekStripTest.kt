package com.ivandsky.puzzlebox.resultui

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.requiredWidth
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.WeekDay
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The week strip's per-day door (nav rethink N2): a button that reports its date. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeekStripTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val monday = LocalDate.of(2026, 11, 2)
    private val days = (0L until 7L).map { WeekDay(monday.plusDays(it), emptyList(), isToday = it == 2L) }

    private fun setContent(onDayClick: (LocalDate) -> Unit) {
        composeRule.setContent {
            PuzzleBoxTheme {
                WeekStrip(days = days, total = 3, onDayClick = onDayClick, modifier = Modifier.requiredWidth(392.dp))
            }
        }
    }

    @Test
    fun `a tapped day reports its date once`() {
        val tapped = mutableListOf<LocalDate>()
        setContent { tapped += it }

        composeRule.onNodeWithTag("week.day.2026-11-04").performClick()

        composeRule.runOnIdle { assertEquals(listOf(LocalDate.of(2026, 11, 4)), tapped) }
    }

    @Test
    fun `each day is a button of at least 48dp`() {
        setContent {}

        days.forEach {
            composeRule.onNodeWithTag("week.day.${it.date}").assertHasClickAction()
                .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
    }
}
