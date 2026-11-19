package com.ivandsky.puzzlebox.insights.stats

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithText
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.domain.recap.SolveRef
import com.ivandsky.puzzlebox.insights.domain.stats.SectionState
import com.ivandsky.puzzlebox.insights.domain.stats.StatsRange
import com.ivandsky.puzzlebox.insights.stats.StatsFixtures.games
import com.ivandsky.puzzlebox.insights.stats.StatsFixtures.queens
import com.ivandsky.puzzlebox.insights.stats.StatsFixtures.zip
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The stats page on a phone: the range chips, the Records rows and what each one opens, and the page's waiting states. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class StatsScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val ranges = mutableListOf<StatsRange>()
    private val opened = mutableListOf<SolveRef>()
    private var backed = 0

    private fun show(state: StatsUiState) = rule.setContent {
        PuzzleBoxTheme {
            StatsScreen(
                state = state,
                games = games,
                gameName = StatsFixtures::gameName,
                onSelectRange = { ranges += it },
                onSelectGame = {},
                onOpenSolve = { opened += it },
                onBack = { backed++ },
            )
        }
    }

    @Test
    fun `the title names the game, or every game`() {
        show(StatsFixtures.ready(game = zip))
        rule.onNodeWithText("Zip stats").assertIsDisplayed()
    }

    @Test
    fun `the title of every game says so`() {
        show(StatsFixtures.ready())
        rule.onNodeWithText("All games stats").assertIsDisplayed()
    }

    @Test
    fun `the picked range chip is selected and a tap on another reports it`() {
        show(StatsFixtures.ready(range = StatsRange.Month))

        rule.onNodeWithTag("stats.range.Month").assertIsSelected()
        rule.onNodeWithTag("stats.range.Week").assertIsNotSelected()
        rule.onNodeWithTag("stats.range.All").assertIsNotSelected()

        rule.onNodeWithTag("stats.range.Week").performClick()

        assertEquals(listOf(StatsRange.Week), ranges)
    }

    @Test
    fun `records list each game's fastest, the longest streak and the longest hint-free run`() {
        show(StatsFixtures.ready())

        listOf("stats.records.fastest.zip", "stats.records.fastest.queens", "stats.records.fastest.tango", "stats.records.streak", "stats.records.hintFree")
            .forEach { rule.onNodeWithTag(it).assertIsDisplayed() }
        rule.onNodeWithTag("stats.records.fastest.zip").assertTextContains("0:41", substring = true)
        rule.onNodeWithTag("stats.records.streak").assertTextContains("14 days", substring = true)
        rule.onNodeWithTag("stats.records.hintFree").assertTextContains("9 solves", substring = true)
    }

    @Test
    fun `a fastest solve with a replay opens that solve`() {
        show(StatsFixtures.ready())

        rule.onNodeWithTag("stats.records.fastest.zip").performClick()

        assertEquals(listOf(SolveRef(zip, StatsFixtures.zipFastest.solve.day, 412, hasReplay = true)), opened)
    }

    @Test
    fun `an imported fastest solve says it has no replay and still opens its day`() {
        show(StatsFixtures.ready())

        rule.onNodeWithTag("stats.records.fastest.queens").assertTextContains("No replay", substring = true).performClick()

        assertEquals(listOf(SolveRef(queens, StatsFixtures.queensFastest.solve.day, 280, hasReplay = false)), opened)
    }

    @Test
    fun `the streak and the hint-free run open the solve they ended on`() {
        show(StatsFixtures.ready())

        rule.onNodeWithTag("stats.records.streak").performClick()
        rule.onNodeWithTag("stats.records.hintFree").performClick()

        assertEquals(listOf(StatsFixtures.streakSolve, StatsFixtures.hintFreeLast), opened)
    }

    @Test
    fun `a streak held only by freezes has no solve to open`() {
        val frozen = StatsFixtures.records.copy(longestStreak = StatsFixtures.records.longestStreak?.copy(solve = null))
        show(StatsFixtures.ready(state = SectionState.Ready(frozen)))

        rule.onNodeWithTag("stats.records.streak").assertIsDisplayed()
        rule.onNode(hasTestTag("stats.records.streak") and hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun `a records section that cannot load says so in its own place`() {
        show(StatsFixtures.ready(state = SectionState.Unavailable))

        rule.onNodeWithTag("stats.records.unavailable").assertIsDisplayed().assertTextContains("Can't load right now")
        rule.onNodeWithTag("stats.records.rows").assertDoesNotExist()
        rule.onNodeWithTag("stats.range").assertIsDisplayed()
    }

    @Test
    fun `a records section still loading shows its own note`() {
        show(StatsFixtures.ready(state = SectionState.Loading))

        rule.onNodeWithTag("stats.records.loading").assertIsDisplayed()
    }

    @Test
    fun `an empty range says so under the chips`() {
        show(StatsUiState(StatsRange.Week, null, StatsBody.Empty))

        rule.onNodeWithTag("stats.empty").assertIsDisplayed()
        rule.onNodeWithTag("stats.range").assertIsDisplayed()
    }

    @Test
    fun `the first restore says the history is coming back`() {
        show(StatsUiState(StatsRange.Month, null, StatsBody.Restoring))

        rule.onNodeWithTag("stats.restoring").assertIsDisplayed()
    }

    @Test
    fun `a phone has no game list`() {
        show(StatsFixtures.ready())

        rule.onNodeWithTag("stats.twoPane").assertDoesNotExist()
        rule.onNodeWithTag("stats.games").assertDoesNotExist()
    }

    @Test
    fun `the back arrow leaves the page`() {
        show(StatsFixtures.ready())

        rule.onNodeWithTag("stats.back").performClick()

        assertEquals(1, backed)
    }
}
