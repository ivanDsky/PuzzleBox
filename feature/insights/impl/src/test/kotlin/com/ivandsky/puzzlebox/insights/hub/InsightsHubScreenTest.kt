package com.ivandsky.puzzlebox.insights.hub

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasContentDescription
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import com.ivandsky.puzzlebox.insights.domain.recap.RecapSummary
import com.ivandsky.puzzlebox.insights.domain.recap.SummarizedPeriod
import com.ivandsky.puzzlebox.model.GameId
import java.time.YearMonth
import org.junit.Assert.assertEquals
import com.ivandsky.puzzlebox.insights.GOLDEN_TABLET
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

    private var statsOpened = 0

    private fun summarized(period: RecapPeriod, solved: Int = 10) =
        SummarizedPeriod(period, RecapSummary(solved, listOf(GameId("alpha") to solved), if (period is RecapPeriod.Year) List(12) { 1 } else emptyList()))

    private var retried = 0

    private fun show(state: InsightsHubUiState) = rule.setContent {
        PuzzleBoxTheme {
            InsightsHubScreen(
                state,
                onOpen = { opened += it },
                onBack = { backed++ },
                onOpenStats = { statsOpened++ },
                onRetry = { retried++ },
            )
        }
    }

    @Test
    fun `a month row opens that month`() {
        show(InsightsHubUiState.Ready(listOf(summarized(november), summarized(october))))

        rule.onNodeWithTag("insights.hub.month.2026-10").assertHasClickAction().performClick()

        assertEquals(listOf(october), opened)
    }

    @Test
    fun `the list starts with a row for the stats of every game`() {
        show(InsightsHubUiState.Ready(listOf(summarized(november))))

        rule.onNodeWithTag("insights.hub.stats").assertIsDisplayed().assertHasClickAction().performClick()

        assertEquals(1, statsOpened)
        assertEquals(emptyList<RecapPeriod>(), opened)
        rule.onNodeWithTag("insights.hub.month.2026-11").assertIsDisplayed()
    }

    @Test
    fun `lists every month`() {
        show(InsightsHubUiState.Ready(listOf(summarized(november), summarized(october))))

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

    @Test
    fun `a failed read offers Try again, which reads again`() {
        show(InsightsHubUiState.Error)

        rule.onNodeWithText("Try again").assertHasClickAction().performClick()

        assertEquals(1, retried)
    }

    @Test
    fun `an unwatched period reads new and a watched one does not`() {
        show(InsightsHubUiState.Ready(listOf(summarized(november, solved = 1_240), summarized(october)), opened = setOf("2026-10")))

        rule.onNodeWithTag("insights.hub.month.2026-11").assert(hasContentDescription("Open your November 2026 recap. 1,240 puzzles solved. New."))
        rule.onNodeWithTag("insights.hub.month.2026-10").assert(hasContentDescription("Open your October 2026 recap. 10 puzzles solved."))
    }

    @Config(qualifiers = GOLDEN_TABLET)
    @Test
    fun `on a tablet a tile selects its cover and the cover opens it`() {
        show(InsightsHubUiState.Ready(listOf(summarized(november), summarized(october))))

        rule.onNodeWithTag("insights.hub.month.2026-10").performClick()

        assertEquals(emptyList<RecapPeriod>(), opened)
        rule.onNodeWithTag("insights.hub.pane").assert(hasContentDescription("Open your October 2026 recap. 10 puzzles solved. New.")).performClick()
        assertEquals(listOf(october), opened)
    }

    @Config(qualifiers = GOLDEN_TABLET)
    @Test
    fun `on a tablet the newest period is selected until another is chosen`() {
        show(InsightsHubUiState.Ready(listOf(summarized(november), summarized(october))))

        rule.onNodeWithTag("insights.hub.pane").performClick()

        assertEquals(listOf(november), opened)
    }
}
