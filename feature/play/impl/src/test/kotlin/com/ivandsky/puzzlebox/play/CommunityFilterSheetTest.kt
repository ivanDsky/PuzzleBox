package com.ivandsky.puzzlebox.play

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level
import com.ivandsky.puzzlebox.play.community.ALPHA
import com.ivandsky.puzzlebox.play.community.BETA
import com.ivandsky.puzzlebox.play.community.CommunityActions
import com.ivandsky.puzzlebox.play.community.CommunityFixture
import com.ivandsky.puzzlebox.play.community.HOUR
import com.ivandsky.puzzlebox.play.community.NOW
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Community's filter as a sheet (backlog #1548) in the phone's tabs, over the real view model and the fake repository:
 * alpha and beta each have a puzzle, alpha's two differ in difficulty. With the flag off it is today's ⋮ menu.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class CommunityFilterSheetTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val fixture = CommunityFixture().also {
        runBlocking {
            it.publish("AAA111", NOW - HOUR, game = ALPHA, difficulty = Level.Hard)
            it.publish("BBB222", NOW - 2 * HOUR, game = ALPHA, difficulty = Level.Easy)
            it.publish("CCC333", NOW - 3 * HOUR, game = BETA, difficulty = Level.Hard)
        }
    }

    private fun show(sheet: Boolean) {
        val flags = if (sheet) FakeReleaseFlags(PlayFlag.CommunityFilterSheet) else FakeReleaseFlags()
        val viewModel = fixture.viewModel(flags)
        rule.setContent {
            PuzzleBoxTheme {
                val state by viewModel.state.collectAsState()
                CommunityTabs(
                    state = state,
                    actions = CommunityActions(
                        onOpen = {},
                        onReport = {},
                        onGame = viewModel::pickGame,
                        onDifficulty = viewModel::pickDifficulty,
                        onClearFilters = viewModel::clearFilters,
                        onRetry = viewModel::retry,
                    ),
                    friends = emptyList(),
                    custom = CustomSlot(yours = { Text("mine") }),
                    onOpen = {},
                    onRemove = {},
                    onEnterCode = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    /** A quick swipe down on the sheet, which a modal sheet that skips its half-height rest takes as dismiss. */
    private fun dismissSheet() {
        rule.onNodeWithTag("community.filter.sheet").performTouchInput { swipeDown(durationMillis = 100) }
        rule.waitForIdle()
        rule.onNodeWithTag("community.filter.sheet").assertDoesNotExist()
    }

    private fun rowsShown() = listOf("AAA111", "BBB222", "CCC333").filter {
        rule.onAllNodes(hasTestTag("community.row.$it")).fetchSemanticsNodes().isNotEmpty()
    }

    /** Bug it catches: the flag on still drawing the ⋮ menu (or the flag off losing it), so the check cannot compare the two. */
    @Test
    fun `the flag on swaps the head's menu for a filter icon above the list, and off keeps the menu`() {
        show(sheet = true)
        rule.onNodeWithTag("community.filter.button").assertIsDisplayed()
        rule.onNodeWithTag("community.filter.menu").assertDoesNotExist()
        rule.onNodeWithTag("community.filter.sheet").assertDoesNotExist()
    }

    /** Bug it catches: the sheet's icon showing in a build with the flag off, beside or instead of the ⋮ menu. */
    @Test
    fun `the flag off is today's menu and no icon`() {
        show(sheet = false)
        rule.onNodeWithTag("community.filter.menu").assertIsDisplayed()
        rule.onNodeWithTag("community.filter.button").assertDoesNotExist()
    }

    /** Bug it catches: the icon left in the head, over the Yours tab, where it filters a list the tab does not hold. */
    @Test
    fun `the filter icon sits under the tabs, in New today's page, and goes with it`() {
        show(sheet = true)
        val tabs = rule.onNodeWithTag("community.tab.yours").getUnclippedBoundsInRoot()
        val icon = rule.onNodeWithTag("community.filter.button").getUnclippedBoundsInRoot()
        assertTrue("icon $icon under tabs $tabs", icon.top >= tabs.bottom)

        tap("community.tab.friends")
        rule.onNodeWithTag("community.filter.button").assertDoesNotExist()
    }

    /** Bug it catches: the icon smaller than a finger, or the chips (the other tap targets) under 48 dp. */
    @Test
    fun `the icon and every chip of the sheet are 48 dp targets`() {
        show(sheet = true)
        val icon = rule.onNodeWithTag("community.filter.button").getUnclippedBoundsInRoot()
        assertTrue(icon.width >= 48.dp && icon.height >= 48.dp)

        tap("community.filter.button")
        // Not assertTouchTargetsAtLeast48: it also counts the sheet's own 32 dp drag handle, which is the design system's.
        // The layout node's size, where a Material chip's minimum-interactive-size padding lands (see TouchTargets.kt).
        val minPx = with(rule.density) { 48.dp.toPx() } - 1f
        CHIPS.forEach { tag ->
            val layout = rule.onNodeWithTag(tag).fetchSemanticsNode().layoutInfo
            assertTrue("$tag ${layout.width} x ${layout.height} px", layout.width >= minPx && layout.height >= minPx)
        }
    }

    /** Bug it catches: a choice missing from the sheet, or one that the dropdown has and the sheet drops. */
    @Test
    fun `the sheet offers All and each game, Any, Easy, Medium and Hard, with All and Any picked`() {
        show(sheet = true)

        tap("community.filter.button")

        rule.onNodeWithTag("community.filter.sheet").assertIsDisplayed()
        rule.onNodeWithTag("community.filter.game.all").assertIsSelected()
        rule.onNodeWithTag("community.filter.game.alpha").assertIsNotSelected()
        rule.onNodeWithTag("community.filter.game.beta").assertIsDisplayed()
        rule.onNodeWithTag("community.filter.difficulty.any").assertIsSelected()
        listOf("easy", "medium", "hard").forEach { rule.onNodeWithTag("community.filter.difficulty.$it").assertIsDisplayed() }
        rule.onNodeWithTag("community.filter.active").assertDoesNotExist()
    }

    /** Bug it catches: a pick that needs a confirm button, closes the sheet, or leaves the list behind it unfiltered. */
    @Test
    fun `a pick applies at once, the sheet stays for the next, and the filter in force is named on the page`() {
        show(sheet = true)
        tap("community.filter.button")

        tap("community.filter.game.alpha")
        rule.onNodeWithTag("community.filter.sheet").assertIsDisplayed()
        rule.onNodeWithTag("community.filter.game.alpha").assertIsSelected()
        rule.onNodeWithTag("community.filter.game.all").assertIsNotSelected()
        assertTrue(rowsShown() == listOf("AAA111", "BBB222"))
        rule.onNodeWithTag("community.filter.active").assertTextEquals("Alpha")

        tap("community.filter.difficulty.hard")
        rule.onNodeWithTag("community.filter.difficulty.hard").assertIsSelected()
        assertTrue(rowsShown() == listOf("AAA111"))
        rule.onNodeWithTag("community.filter.active").assertTextEquals("Alpha · Hard")
    }

    /** Bug it catches: a filter that can only be undone by reopening the sheet, or a clear that drops one pick of two. */
    @Test
    fun `the filter chip on the page clears both picks without opening the sheet`() {
        show(sheet = true)
        tap("community.filter.button")
        tap("community.filter.game.alpha")
        tap("community.filter.difficulty.hard")
        dismissSheet()
        rule.onNodeWithTag("community.filter.active").assertIsDisplayed()
        rule.onNodeWithContentDescription("Clear filter").assertIsDisplayed()

        tap("community.filter.active")

        rule.onNodeWithTag("community.filter.active").assertDoesNotExist()
        assertTrue(rowsShown() == listOf("AAA111", "BBB222", "CCC333"))
        tap("community.filter.button")
        rule.onNodeWithTag("community.filter.game.all").assertIsSelected()
        rule.onNodeWithTag("community.filter.difficulty.any").assertIsSelected()
    }

    /** Bug it catches: the All and Any chips in the sheet not clearing their own group (the dropdown's two clear items). */
    @Test
    fun `All and Any clear one group each`() {
        show(sheet = true)
        tap("community.filter.button")
        tap("community.filter.game.alpha")
        tap("community.filter.difficulty.hard")

        tap("community.filter.game.all")
        rule.onNodeWithTag("community.filter.active").assertTextEquals("Hard")
        assertTrue(rowsShown() == listOf("AAA111", "CCC333"))

        tap("community.filter.difficulty.any")
        rule.onNodeWithTag("community.filter.active").assertDoesNotExist()
        assertTrue(rowsShown() == listOf("AAA111", "BBB222", "CCC333"))
    }

    private companion object {
        val CHIPS = listOf("game.all", "game.alpha", "game.beta", "difficulty.any", "difficulty.easy", "difficulty.medium", "difficulty.hard")
            .map { "community.filter.$it" }
    }
}
