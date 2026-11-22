package com.ivandsky.puzzlebox.insights.stats

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.domain.stats.StatsRange
import com.ivandsky.puzzlebox.insights.stats.StatsFixtures.games
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The stats page on a tablet with the filters flag on: "Overall" leads the list, the title names the scope and a quiet Since sits atop the pane. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class StatsFilterTabletTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val ranges = mutableListOf<StatsRange>()

    private fun show(game: GameId?, range: StatsRange = StatsRange.Week) = rule.setContent {
        PuzzleBoxTheme {
            StatsScreen(
                state = StatsFixtures.ready(range = range, game = game, filters = true, since = StatsFixtures.since),
                games = games,
                gameName = StatsFixtures::gameName,
                onSelectRange = { ranges += it },
                onSelectGame = {},
                onOpenSolve = {},
                onBack = {},
            )
        }
    }

    @Test
    fun `the first row of the list is Overall and the title says Overall stats`() {
        show(game = null)

        rule.onNodeWithTag("stats.game.all").assertIsSelected().assertTextContains("Overall", substring = true)
        rule.onNodeWithText("Overall stats").assertIsDisplayed()
    }

    @Test
    fun `a game picked names the title`() {
        show(game = StatsFixtures.zip)

        rule.onNodeWithText("Zip stats").assertIsDisplayed()
    }

    @Test
    fun `the pane has a Since dropdown and no game dropdown or chips`() {
        show(game = null)

        rule.onNodeWithTag("stats.filters").assertIsDisplayed().assertHeightIsAtLeast(52.dp)
        rule.onNodeWithTag("stats.filter.since").assertIsDisplayed().assertTextContains("Since Nov 13", substring = true)
        rule.onNodeWithTag("stats.filter.game").assertDoesNotExist()
        rule.onNodeWithTag("stats.range").assertDoesNotExist()
    }

    @Test
    fun `the quiet Since opens the same sheet and a row picks its range`() {
        show(game = null)

        rule.onNodeWithTag("stats.filter.since").performClick()
        rule.onNodeWithTag("stats.filter.since.sheet").assertIsDisplayed()
        rule.onNodeWithTag("stats.filter.since.All").performClick()

        assertEquals(listOf(StatsRange.All), ranges)
    }
}
