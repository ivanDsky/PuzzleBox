package com.ivandsky.puzzlebox.play.community

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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

/** New today's filters as two chips and their sheets (epic picker-filters, ticket 05), through [CommunityPageHost] over the fake repository. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class CommunityFilterChipsTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun stopGraph() = stopKoin()

    private val fixture = CommunityFixture().also {
        runBlocking {
            it.publish("AAA111", NOW - 1 * HOUR, game = ALPHA, difficulty = Level.Hard)
            it.publish("AAA222", NOW - 2 * HOUR, game = ALPHA, difficulty = Level.Easy)
            it.publish("AAA333", NOW - 3 * HOUR, game = ALPHA, difficulty = Level.Medium)
            it.publish("BBB111", NOW - 4 * HOUR, game = BETA, difficulty = Level.Hard)
            it.publish("BBB222", NOW - 5 * HOUR, game = BETA, difficulty = Level.Expert)
            it.publish("BBB333", NOW - 6 * HOUR, game = BETA, difficulty = Level.Easy)
        }
    }

    private fun show(fontScale: Float = 1f) {
        stopKoin()
        startKoin { modules(module { viewModel { fixture.viewModel() } }) }
        rule.setContent { Host(fontScale) }
        rule.waitForIdle()
    }

    @Composable
    private fun Host(fontScale: Float) {
        PuzzleBoxTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                CommunityPageHost()
            }
        }
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        rule.waitForIdle()
    }

    private fun sheets(tag: String) = rule.onAllNodesWithTag("$tag.sheet").fetchSemanticsNodes().size

    private fun rows() = rule.onAllNodes(SemanticsMatcher("a community row") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.matches(Regex("community\\.row\\.[A-Z0-9]{6}")) == true
    }).fetchSemanticsNodes().map { it.config.getOrNull(SemanticsProperties.TestTag)!!.removePrefix("community.row.") }

    private fun top(tag: String) = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot().top

    @Test
    fun `the chips show with the game and difficulty names`() {
        show()

        rule.onNodeWithTag("community.filter.game").assertIsDisplayed().assertTextEquals("Game")
        rule.onNodeWithTag("community.filter.difficulty").assertIsDisplayed().assertTextEquals("Difficulty")
    }

    @Test
    fun `the Game sheet lists All games then each game, picking one closes it, reads on the chip and narrows the list`() {
        show()

        tap("community.filter.game")
        assertEquals(1, sheets("community.filter.game"))
        rule.onNode(hasText("Game") and hasAnyAncestor(hasTestTag("community.filter.game.sheet"))).assertIsDisplayed()
        rule.onNodeWithTag("community.filter.game.all").assertIsSelected()
        assertTrue(top("community.filter.game.all") < top("community.filter.game.alpha"))
        assertTrue(top("community.filter.game.alpha") < top("community.filter.game.beta"))
        rule.onNodeWithTag("community.filter.game.sheet.close").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithTag("community.filter.game.gamma").fetchSemanticsNodes().size)

        tap("community.filter.game.beta")

        assertEquals(0, sheets("community.filter.game"))
        rule.onNodeWithTag("community.filter.game").assertTextEquals("Beta")
        assertEquals(listOf("BBB111", "BBB222", "BBB333"), rows())
        assertEquals(BETA.value, fixture.recording.filters.last().gameId)
    }

    @Test
    fun `the picked game is the checked row, and All games resets the chip and the list`() {
        show()
        tap("community.filter.game")
        tap("community.filter.game.beta")

        tap("community.filter.game")
        rule.onNodeWithTag("community.filter.game.beta").assertIsSelected()
        rule.onNodeWithTag("community.filter.game.all").assertIsNotSelected()
        tap("community.filter.game.all")

        rule.onNodeWithTag("community.filter.game").assertTextEquals("Game")
        assertEquals(6, rows().size)
    }

    @Test
    fun `the Difficulty sheet has Any difficulty, Easy, Medium and Hard with their marks, and Hard also shows Expert`() {
        show()

        tap("community.filter.difficulty")
        rule.onNodeWithTag("community.filter.difficulty.any").assertIsSelected()
        listOf("easy", "medium", "hard").forEach { level ->
            rule.onNode(hasTestTag("difficulty.$level") and hasAnyAncestor(hasTestTag("community.filter.difficulty.$level")), useUnmergedTree = true)
                .assertExists()
        }
        assertEquals(0, rule.onAllNodesWithTag("community.filter.difficulty.expert").fetchSemanticsNodes().size)
        rule.onNode(hasTestTag("difficulty.expert") and hasAnyAncestor(hasTestTag("community.filter.difficulty.sheet")), useUnmergedTree = true).assertDoesNotExist()
        rule.onNode(hasAnyAncestor(hasTestTag("community.filter.difficulty.any")) and hasTestTag("difficulty.easy"), useUnmergedTree = true)
            .assertDoesNotExist()
        tap("community.filter.difficulty.hard")

        rule.onNodeWithTag("community.filter.difficulty").assertTextEquals("Hard")
        assertEquals(listOf("AAA111", "BBB111", "BBB222"), rows())
    }

    @Test
    fun `the checked difficulty again drops it and the chip reads Difficulty`() {
        show()
        tap("community.filter.difficulty")
        tap("community.filter.difficulty.easy")
        assertEquals(listOf("AAA222", "BBB333"), rows())

        tap("community.filter.difficulty")
        tap("community.filter.difficulty.easy")

        rule.onNodeWithTag("community.filter.difficulty").assertTextEquals("Difficulty")
        assertEquals(6, rows().size)
    }

    @Test
    fun `the count line says how many puzzles are listed, and 1 puzzle in the singular`() {
        show()
        rule.onNodeWithTag("community.filter.count").assertTextEquals("6 puzzles")

        tap("community.filter.game")
        tap("community.filter.game.beta")
        tap("community.filter.difficulty")
        tap("community.filter.difficulty.easy")

        rule.onNodeWithTag("community.filter.count").assertTextEquals("1 puzzle")
    }

    @Test
    fun `an offline list shows the chips and no count`() {
        fixture.shared.online = false
        show()
        rule.onNodeWithTag("community.filter.count").assertDoesNotExist()
        rule.onNodeWithTag("community.filter.game").assertIsDisplayed()
    }

    @Test
    fun `no matches says so with Clear filters, which drops both filters in one tap, and an unfiltered empty list keeps its own message`() {
        show()
        tap("community.filter.game")
        tap("community.filter.game.beta")
        tap("community.filter.difficulty")
        tap("community.filter.difficulty.medium")

        rule.onNodeWithText("No community puzzle matches these filters.").assertIsDisplayed()
        rule.onNodeWithTag("community.empty").assertDoesNotExist()
        rule.onNodeWithTag("community.filter.count").assertDoesNotExist()
        val reloads = fixture.recording.filters.size
        tap("community.filter.empty.clear")

        assertEquals(reloads + 1, fixture.recording.filters.size)
        rule.onNodeWithTag("community.filter.game").assertTextEquals("Game")
        rule.onNodeWithTag("community.filter.difficulty").assertTextEquals("Difficulty")
        assertEquals(6, rows().size)
    }

    @Test
    fun `an empty list with no filter on keeps the plain empty message and no Clear filters`() {
        val none = CommunityFixture()
        stopKoin()
        startKoin { modules(module { viewModel { none.viewModel() } }) }
        rule.setContent { Host(1f) }
        rule.waitForIdle()

        rule.onNodeWithTag("community.empty").assertIsDisplayed()
        rule.onNodeWithTag("community.filter.empty").assertDoesNotExist()
        rule.onNodeWithTag("community.filter.count").assertDoesNotExist()
    }

    @Test
    fun `the chips and their sheet rows are at least 48dp tall`() {
        show()
        rule.onNodeWithTag("community.filter.game").assertTouchHeightIsEqualTo(48.dp)
        rule.onNodeWithTag("community.filter.difficulty").assertTouchHeightIsEqualTo(48.dp)

        tap("community.filter.game")
        rule.onNodeWithTag("community.filter.game.all").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("community.filter.game.alpha").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("community.filter.game.sheet.close").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `an open sheet is still open after a rotation, with the pick kept`() {
        val restoration = StateRestorationTester(rule)
        stopKoin()
        startKoin { modules(module { viewModel { fixture.viewModel() } }) }
        restoration.setContent { Host(1f) }
        rule.waitForIdle()
        tap("community.filter.game")

        restoration.emulateSavedInstanceStateRestore()
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        rule.waitForIdle()

        assertEquals(1, sheets("community.filter.game"))
    }

    @Test
    fun `at font scale 1 point 3 the chip row scrolls to the Difficulty chip`() {
        show(fontScale = 1.3f)
        tap("community.filter.game")
        tap("community.filter.game.alpha")

        rule.onNodeWithTag("community.filter.difficulty").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("community.filter.game").assertTextContains("Alpha")
    }

    private companion object {
        const val SETTLE_MS = 1000L
    }
}
