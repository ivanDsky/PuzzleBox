package com.ivandsky.puzzlebox.play.community.shelves.seeall

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.play.community.ALPHA
import com.ivandsky.puzzlebox.play.community.BETA
import com.ivandsky.puzzlebox.play.community.DifficultyChoice
import com.ivandsky.puzzlebox.play.community.shelves.codeOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The See all page for each [SeeAllUiState]: its chrome, its filter bar, its no-match state, and what a tap reports. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SeeAllScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var state by mutableStateOf(SeeAllUiState())
    private val calls = mutableListOf<String>()

    private val actions = SeeAllActions(
        onBack = { calls += "back" },
        onQuery = { calls += "query $it" },
        onGame = { calls += "game ${it?.value}" },
        onDifficulty = { calls += "difficulty ${it?.name}" },
        onSort = { calls += "sort ${it.label}" },
        onClearFilters = { calls += "clear" },
        onOpen = { calls += "open ${it.value}" },
        onReport = { calls += "report ${it.value}" },
        onRetry = { calls += "retry" },
    )

    private fun show(initial: SeeAllUiState) {
        state = initial
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.requiredSize(412.dp, 900.dp)) { SeeAllScreen(state, actions) }
            }
        }
    }

    @Test
    fun `the title names the scope and Back goes back`() {
        show(SeeAllSampleStates.ready(SeeAllScope.MostPlayed))
        composeRule.onNodeWithTag("seeall.title").assertTextEquals("Most played")
        composeRule.onNodeWithTag("seeall.back").performClick()
        assertEquals(listOf("back"), calls)
    }

    @Test
    fun `a game's page is titled with the game and has no game filter`() {
        show(SeeAllSampleStates.ready(SeeAllScope.Game(ALPHA)))
        composeRule.onNodeWithTag("seeall.title").assertTextEquals("Alpha")
        composeRule.onNodeWithTag("seeall.filter.game").assertDoesNotExist()
        composeRule.onNodeWithTag("seeall.filter.difficulty").assertExists()
    }

    @Test
    fun `the count line and the rows`() {
        show(SeeAllSampleStates.ready(count = 3))
        composeRule.onNodeWithText("3 puzzles").assertExists()
        composeRule.onNodeWithTag("community.row.${codeOf(1).value}").assertExists()
        state = SeeAllSampleStates.ready(count = 1)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("1 puzzle").assertExists()
    }

    @Test
    fun `a row opens and its menu reports`() {
        show(SeeAllSampleStates.ready(count = 2))
        composeRule.onNodeWithTag("community.row.${codeOf(2).value}").performClick()
        composeRule.onNodeWithTag("community.row.${codeOf(1).value}.more").performClick()
        composeRule.onNodeWithTag("community.row.${codeOf(1).value}.report").performClick()
        assertEquals(listOf("open ${codeOf(2).value}", "report ${codeOf(1).value}"), calls)
    }

    @Test
    fun `typing reports the query and the Search scope starts focused`() {
        show(SeeAllSampleStates.ready(SeeAllScope.Search))
        composeRule.onNodeWithTag("seeall.search").assertIsFocused()
        composeRule.onNodeWithTag("seeall.search").performTextInput("a")
        assertEquals(listOf("query a"), calls)
    }

    @Test
    fun `a scoped page does not grab focus`() {
        show(SeeAllSampleStates.ready(SeeAllScope.Newest))
        composeRule.onNodeWithTag("seeall.search").assertExists()
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `the game filter opens its sheet and a pick is reported`() {
        show(SeeAllSampleStates.ready())
        composeRule.onNodeWithText("Any game").performClick()
        composeRule.onNodeWithText("Beta").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("game ${BETA.value}"), calls)
    }

    @Test
    fun `the difficulty filter opens its sheet and a pick is reported`() {
        show(SeeAllSampleStates.ready())
        composeRule.onNodeWithText("Any difficulty").performClick()
        composeRule.onNodeWithText("Hard").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("difficulty Hard"), calls)
    }

    @Test
    fun `a set filter shows its value in place of the label`() {
        show(SeeAllSampleStates.ready(difficulty = DifficultyChoice.Easy))
        composeRule.onNodeWithText("Easy").assertExists()
        composeRule.onNodeWithText("Any difficulty").assertDoesNotExist()
    }

    @Test
    fun `the sort menu lists what the state offers and a pick is reported`() {
        show(SeeAllSampleStates.ready(SeeAllScope.Search).copy(sortOptions = SeeAllSort.entries, sort = SeeAllSort.MostPlayed))
        composeRule.onNodeWithTag("seeall.sort").performClick()
        composeRule.onNodeWithText("Newest").performClick()
        assertEquals(listOf("sort Newest"), calls)
    }

    @Test
    fun `nothing matches says so and Clear filters clears`() {
        show(SeeAllSampleStates.noMatch("zebra"))
        composeRule.onNodeWithText("Nothing matches “zebra”").assertExists()
        composeRule.onNodeWithText("Try another word, or clear the filters.").assertExists()
        composeRule.onNodeWithTag("seeall.nomatch.clear").performClick()
        assertEquals(listOf("clear"), calls)
    }

    @Test
    fun `loading offline and refused states`() {
        show(SeeAllUiState(scope = SeeAllScope.Newest, phase = SeeAllPhase.Loading))
        composeRule.onNodeWithTag("seeall.loading").assertExists()
        state = SeeAllUiState(scope = SeeAllScope.Newest, phase = SeeAllPhase.Offline)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("You're offline").assertExists()
        composeRule.onNodeWithText("Retry").performClick()
        state = SeeAllUiState(scope = SeeAllScope.Newest, phase = SeeAllPhase.Refused)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Couldn't load puzzles").assertExists()
        assertEquals(listOf("retry"), calls)
    }

    @Test
    fun `an empty scope says there is nothing yet`() {
        show(SeeAllUiState(scope = SeeAllScope.Friends, phase = SeeAllPhase.Ready(emptyList())))
        composeRule.onNodeWithTag("seeall.empty").assertExists()
        composeRule.onNodeWithText("No puzzles from friends yet").assertExists()
    }
}
