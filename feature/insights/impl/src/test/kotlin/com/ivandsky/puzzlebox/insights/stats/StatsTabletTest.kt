package com.ivandsky.puzzlebox.insights.stats

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.stats.StatsFixtures.games
import com.ivandsky.puzzlebox.insights.stats.StatsFixtures.queens
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The stats page on a tablet: the games list beside the page, its picked game marked, and a tap switching it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class StatsTabletTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val picked = mutableListOf<GameId?>()

    private fun show(game: GameId?) = rule.setContent {
        PuzzleBoxTheme {
            StatsScreen(
                state = StatsFixtures.ready(game = game),
                games = games,
                gameName = StatsFixtures::gameName,
                onSelectRange = {},
                onSelectGame = { picked += it },
                onOpenSolve = {},
                onBack = {},
            )
        }
    }

    @Test
    fun `shows the games list beside the page, every game picked first`() {
        show(game = null)

        rule.onNodeWithTag("stats.twoPane").assertIsDisplayed()
        rule.onNodeWithTag("stats.games").assertIsDisplayed()
        rule.onNodeWithTag("stats.game.all").assertIsSelected()
        rule.onNodeWithTag("stats.game.zip").assertIsNotSelected()
        rule.onNodeWithTag("stats.records").assertIsDisplayed()
        rule.onNodeWithTag("stats.range").assertIsDisplayed()
    }

    @Test
    fun `the page's game is the one marked`() {
        show(game = queens)

        rule.onNodeWithTag("stats.game.queens").assertIsSelected()
        rule.onNodeWithTag("stats.game.all").assertIsNotSelected()
    }

    @Test
    fun `a game in the list switches the page to it and the first row switches back to every game`() {
        show(game = null)

        rule.onNodeWithTag("stats.game.tango").performClick()
        rule.onNodeWithTag("stats.game.all").performClick()

        assertEquals(listOf<GameId?>(StatsFixtures.tango, null), picked)
    }
}
