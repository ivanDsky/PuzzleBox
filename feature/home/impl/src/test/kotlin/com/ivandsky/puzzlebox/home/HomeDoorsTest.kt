package com.ivandsky.puzzlebox.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
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
 * Today's doors (nav rethink N1-N4): a week disc, a Recent row, the streak row and the
 * day-detail link each report their request once through [HomeSummaries].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeDoorsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val opened = mutableListOf<String>()
    private var streakOpened = 0
    private val day = HistoryDay("2026-11-03", "November 3", listOf(solvedZip()))

    private fun setContent(layout: HomeLayout, picked: String? = null) {
        val summaries = HomeSummaries(
            onOpenRank = null,
            duels = null,
            onOpenDay = { iso -> opened += iso },
            onOpenStreak = { streakOpened++ },
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalTabReselect provides MutableSharedFlow()) {
                PuzzleBoxTheme {
                    var pick by remember { mutableStateOf(picked) }
                    HomeScreen(
                        layout = layout,
                        state = state(TODAY, listOf(day), GameId("tango")).copy(selectedDay = pick),
                        onOpenGame = {},
                        onSelectDay = { pick = it },
                        summaries = summaries,
                        title = "PuzzleBox",
                        modifier = Modifier,
                    )
                }
            }
        }
    }

    @Test
    fun `phone week disc, streak row and recent row each report once`() {
        setContent(HomeLayout.SinglePane)

        composeRule.onNodeWithTag("week.day.2026-11-02").performScrollTo().performClick()
        composeRule.onNodeWithTag("home.streak_door").performScrollTo().performClick()
        composeRule.onNodeWithTag("home.history_row_2026-11-03").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("2026-11-02", "2026-11-03"), opened)
            assertEquals(1, streakOpened)
        }
    }

    @Test
    fun `phone doors are at least 48dp tall`() {
        setContent(HomeLayout.SinglePane)

        composeRule.onNodeWithTag("week.day.2026-11-02").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("home.streak_door").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("home.history_row_2026-11-03").assertHeightIsAtLeast(48.dp)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
    fun `at 360dp each week disc zone is touchable at 48dp wide and the zones leave no gap`() {
        setContent(HomeLayout.SinglePane)

        val cells = (0L..6L).map { composeRule.onNodeWithTag("week.day.${HomeScreenTest.DAY.minusDays(6 - it)}") }
        cells.forEach { it.assertTouchWidthIsEqualTo(48.dp).assertHeightIsAtLeast(48.dp) }
        val bounds = cells.map { it.getUnclippedBoundsInRoot() }
        println("week zone at 360dp: layout ${bounds.first().width}, touch 48.dp")
        bounds.zipWithNext { a, b -> assertEquals(a.right.value, b.left.value, 0.5f) }
    }

    @Test
    fun `tablet streak pane week disc and streak row report once`() {
        setContent(HomeLayout.TwoPane)

        composeRule.onNodeWithTag("week.day.2026-10-30").performScrollTo().performClick()
        composeRule.onNodeWithTag("home.streak_door").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("2026-10-30"), opened)
            assertEquals(1, streakOpened)
        }
    }

    @Test
    fun `tablet recent row still selects the day instead of opening it`() {
        setContent(HomeLayout.TwoPane)

        composeRule.onNodeWithTag("home.history_row_2026-11-03").performScrollTo().performClick()

        composeRule.onNodeWithTag("home.day_detail").assertExists()
        composeRule.runOnIdle { assertEquals(emptyList<String>(), opened) }
    }

    @Test
    fun `tablet day detail button opens that day once`() {
        setContent(HomeLayout.TwoPane, picked = "2026-11-03")

        composeRule.onNodeWithTag("home.day_open_ranks").assertHeightIsAtLeast(48.dp).performClick()

        composeRule.runOnIdle { assertEquals(listOf("2026-11-03"), opened) }
    }
}
