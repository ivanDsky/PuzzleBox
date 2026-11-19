package com.ivandsky.puzzlebox.insights.hub

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The hub's screen: one tappable row per month, and a quiet message when there are none. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class InsightsHubScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val opened = mutableListOf<RecapPeriod>()
    private var backed = 0
    private val november = RecapPeriod.Month(YearMonth.of(2026, 11))
    private val october = RecapPeriod.Month(YearMonth.of(2026, 10))

    private fun show(state: InsightsHubUiState) = rule.setContent {
        PuzzleBoxTheme { InsightsHubScreen(state, onOpen = { opened += it }, onBack = { backed++ }) }
    }

    @Test
    fun `a month row opens that month`() {
        show(InsightsHubUiState.Ready(listOf(november, october)))

        rule.onNodeWithTag("insights.hub.month.2026-10").assertHasClickAction().performClick()

        assertEquals(listOf(october), opened)
    }

    @Test
    fun `lists every month`() {
        show(InsightsHubUiState.Ready(listOf(november, october)))

        rule.onNodeWithTag("insights.hub.month.2026-11").assertIsDisplayed()
        rule.onNodeWithTag("insights.hub.month.2026-10").assertIsDisplayed()
    }

    @Test
    fun `no months says there are no recaps yet`() {
        show(InsightsHubUiState.Empty)

        rule.onNodeWithTag("insights.hub.empty").assertIsDisplayed()
        rule.onNodeWithTag("insights.hub.list").assertDoesNotExist()
    }

    @Test
    fun `no months during the first restore says the history is coming back`() {
        show(InsightsHubUiState.Restoring)

        rule.onNodeWithTag("insights.hub.restoring").assertIsDisplayed()
    }

    @Test
    fun `the back arrow leaves the hub`() {
        show(InsightsHubUiState.Empty)

        rule.onNodeWithTag("insights.hub.back").performClick()

        assertEquals(1, backed)
    }
}
