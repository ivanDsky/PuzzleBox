package com.ivandsky.puzzlebox.play.community

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The community filter ⋮ menu and the active-filter line, through [CommunityHost] over the fake repository. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class CommunityFilterMenuTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun stopGraph() = stopKoin()

    private fun show(fixture: CommunityFixture) {
        stopKoin()
        startKoin { modules(module { viewModel { fixture.viewModel() } }) }
        rule.setContent {
            PuzzleBoxTheme { CommunityHost(onOpen = {}) { community -> LazyColumn { community(this) } } }
        }
        rule.waitForIdle()
    }

    private fun fixture() = CommunityFixture().also {
        runBlocking {
            it.publish("AAA111", NOW - HOUR, game = ALPHA, difficulty = Level.Hard)
            it.publish("BBB222", NOW - 2 * HOUR, game = ALPHA, difficulty = Level.Easy)
            it.publish("CCC333", NOW - 3 * HOUR, game = BETA, difficulty = Level.Hard)
        }
    }

    private fun pick(tag: String) {
        rule.onNodeWithTag("community.filter.menu").performClick()
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    @Test
    fun `no chip rows, a 48dp Filter puzzles button, and no active line while nothing is filtered`() {
        show(fixture())

        rule.onNodeWithTag("community.games").assertDoesNotExist()
        rule.onNodeWithTag("community.difficulty").assertDoesNotExist()
        rule.onNodeWithContentDescription("Filter puzzles").assertIsDisplayed()
        val bounds = rule.onNodeWithTag("community.filter.menu").getUnclippedBoundsInRoot()
        assertTrue(bounds.width >= 48.dp && bounds.height >= 48.dp)
        rule.onNodeWithTag("community.filter.active").assertDoesNotExist()
    }

    @Test
    fun `the menu offers All and each game, Any, Easy, Medium and Hard, with the current pick checked`() {
        show(fixture())

        rule.onNodeWithTag("community.filter.menu").performClick()

        rule.onNodeWithTag("community.filter.game.all").assertIsSelected()
        rule.onNodeWithTag("community.filter.game.alpha").assertIsDisplayed()
        rule.onNodeWithTag("community.filter.game.beta").assertIsDisplayed()
        rule.onNodeWithTag("community.filter.difficulty.any").assertIsSelected()
        listOf("easy", "medium", "hard").forEach { rule.onNodeWithTag("community.filter.difficulty.$it").assertIsDisplayed() }
    }

    @Test
    fun `each difficulty item leads with its level's mark, Hard with the Hard mark, and Any has none`() {
        show(CommunityFixture())

        rule.onNodeWithTag("community.filter.menu").performClick()

        listOf("easy", "medium", "hard").forEach {
            rule.onNode(hasTestTag("difficulty.$it") and hasAnyAncestor(hasTestTag("community.filter.difficulty.$it")), useUnmergedTree = true)
                .assertIsDisplayed()
        }
        rule.onNode(hasTestTag("difficulty.expert"), useUnmergedTree = true).assertDoesNotExist()
        rule.onNode(hasAnyAncestor(hasTestTag("community.filter.difficulty.any")) and hasTestTag("difficulty.easy"), useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun `picking a game and a difficulty narrows the list and names both in one line`() {
        show(fixture())

        pick("community.filter.game.alpha")
        rule.onNodeWithTag("community.row.CCC333").assertDoesNotExist()
        rule.onNodeWithTag("community.filter.active").assertTextEquals("Alpha")
        pick("community.filter.difficulty.hard")

        rule.onNodeWithTag("community.row.AAA111").assertIsDisplayed()
        rule.onNodeWithTag("community.row.BBB222").assertDoesNotExist()
        rule.onNodeWithTag("community.row.CCC333").assertDoesNotExist()
        rule.onNodeWithTag("community.filter.active").assertTextContains("Alpha · Hard")
        rule.onNodeWithTag("community.filter.menu").performClick()
        rule.onNodeWithTag("community.filter.game.alpha").assertIsSelected()
        rule.onNodeWithTag("community.filter.difficulty.hard").assertIsSelected()
    }

    @Test
    fun `the clear target on the line drops both filters and the line`() {
        show(fixture())
        pick("community.filter.game.beta")
        pick("community.filter.difficulty.hard")
        rule.onNodeWithTag("community.row.CCC333").assertIsDisplayed()
        val bounds = rule.onNodeWithTag("community.filter.clear").getUnclippedBoundsInRoot()
        assertTrue(bounds.width >= 48.dp && bounds.height >= 48.dp)

        rule.onNodeWithTag("community.filter.clear").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("community.filter.active").assertDoesNotExist()
        listOf("AAA111", "BBB222", "CCC333").forEach { rule.onNodeWithTag("community.row.$it").assertIsDisplayed() }
    }

    @Test
    fun `Any and All in the menu clear one filter each`() {
        show(fixture())
        pick("community.filter.game.alpha")
        pick("community.filter.difficulty.easy")
        rule.onNodeWithTag("community.filter.active").assertTextContains("Alpha · Easy")

        pick("community.filter.difficulty.any")
        rule.onNodeWithTag("community.filter.active").assertTextEquals("Alpha")
        pick("community.filter.game.all")

        rule.onNodeWithTag("community.filter.active").assertDoesNotExist()
        rule.onNodeWithTag("community.row.CCC333").assertIsDisplayed()
    }
}
