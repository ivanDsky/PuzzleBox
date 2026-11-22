package com.ivandsky.puzzlebox.insights.stats

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.domain.stats.StatsRange
import com.ivandsky.puzzlebox.insights.stats.StatsFixtures.games
import com.ivandsky.puzzlebox.insights.stats.StatsFixtures.zip
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The stats page's filter bar on a phone (flag on): Any game and Since as text dropdowns, each opening its sheet. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class StatsFilterBarTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val ranges = mutableListOf<StatsRange>()
    private val picked = mutableListOf<GameId?>()

    private fun show(state: StatsUiState) = rule.setContent {
        PuzzleBoxTheme {
            StatsScreen(
                state = state,
                games = games,
                gameName = StatsFixtures::gameName,
                onSelectRange = { ranges += it },
                onSelectGame = { picked += it },
                onOpenSolve = {},
                onBack = {},
            )
        }
    }

    private fun on(range: StatsRange = StatsRange.Week, game: GameId? = null) =
        StatsFixtures.ready(range = range, game = game, filters = true, since = StatsFixtures.since)

    @Test
    fun `the bar replaces the range chips and the title stays Stats whatever game is picked`() {
        show(on(game = zip))

        rule.onNodeWithTag("stats.filters").assertIsDisplayed()
        rule.onNodeWithTag("stats.range").assertDoesNotExist()
        rule.onNodeWithText("Stats").assertIsDisplayed()
        rule.onNodeWithText("Zip stats").assertDoesNotExist()
    }

    @Test
    fun `the game dropdown says Any game until one is picked, then names it`() {
        show(on())
        rule.onNodeWithTag("stats.filter.game").assertTextContains("Any game", substring = true)
    }

    @Test
    fun `a picked game is named in its dropdown`() {
        show(on(game = zip))
        rule.onNodeWithTag("stats.filter.game").assertTextContains("Zip", substring = true)
    }

    @Test
    fun `the since dropdown words the range from the puzzle day`() {
        show(on(range = StatsRange.Week))
        rule.onNodeWithTag("stats.filter.since").assertTextContains("Since Nov 13", substring = true)
    }

    @Test
    fun `the game sheet lists Any game then every game and a tap on a game picks it`() {
        show(on())

        rule.onNodeWithTag("stats.filter.game").performClick()

        rule.onNodeWithTag("stats.filter.game.sheet").assertIsDisplayed()
        rule.onNodeWithTag("stats.filter.game.any").assertIsSelected()
        listOf("zip", "queens", "tango").forEach { rule.onNodeWithTag("stats.filter.game.$it").assertIsDisplayed() }

        rule.onNodeWithTag("stats.filter.game.queens").performClick()

        assertEquals(listOf<GameId?>(StatsFixtures.queens), picked)
        assertEquals(emptyList<StatsRange>(), ranges)
    }

    @Test
    fun `the Any game row clears the game`() {
        show(on(game = zip))

        rule.onNodeWithTag("stats.filter.game").performClick()
        rule.onNodeWithTag("stats.filter.game.zip").assertIsSelected()
        rule.onNodeWithTag("stats.filter.game.any").performClick()

        assertEquals(listOf<GameId?>(null), picked)
    }

    @Test
    fun `the since sheet has three rows with sub-lines, no all row and the records footer, and a row picks its range`() {
        show(on(range = StatsRange.Week))

        rule.onNodeWithTag("stats.filter.since").performClick()

        rule.onNodeWithTag("stats.filter.since.sheet").assertIsDisplayed()
        rule.onNodeWithTag("stats.filter.since.Week").assertIsSelected().assertTextContains("Last 7 days", substring = true)
        rule.onNodeWithTag("stats.filter.since.Month").assertTextContains("Start of November", substring = true)
        rule.onNodeWithTag("stats.filter.since.All").assertTextContains("Mar 30, 2025", substring = true)
        rule.onNodeWithText("Records always count all time.").assertIsDisplayed()
        // The bar behind the sheet still says "Any game", so look inside the sheet only.
        val inSheet = hasAnyAncestor(hasTestTag("stats.filter.since.sheet"))
        rule.onNode(inSheet and hasText("Any", substring = true)).assertDoesNotExist()
        rule.onNode(inSheet and hasText("All games", substring = true)).assertDoesNotExist()
        rule.onNode(inSheet and hasText("Last 7 days", substring = true)).assertIsDisplayed()

        rule.onNodeWithTag("stats.filter.since.Month").performClick()

        assertEquals(listOf(StatsRange.Month), ranges)
        assertEquals(emptyList<GameId?>(), picked)
    }

    @Test
    fun `with the flag off the chips stay and there is no bar`() {
        show(StatsFixtures.ready())

        rule.onNodeWithTag("stats.range").assertIsDisplayed()
        rule.onNodeWithTag("stats.filters").assertDoesNotExist()
        rule.onNodeWithText("All games stats").assertIsDisplayed()
    }
}
