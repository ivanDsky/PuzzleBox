package com.ivandsky.puzzlebox.app.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SC2 contract for the shell's tabs: the bar and the rail each render exactly one
 * `nav.tab.<name>` item per top-level destination, only the selected destination's item
 * carries selected semantics, and a tap reports that destination's key. SC11: a badged key
 * shows the described dot on its own item only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TopLevelNavBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `bar highlights exactly the selected destination`() {
        assertHighlight { selected, onSelect -> TopLevelNavBar(selected = selected, onSelect = onSelect) }
    }

    @Test
    fun `rail highlights exactly the selected destination`() {
        assertHighlight { selected, onSelect -> TopLevelNavRail(selected = selected, onSelect = onSelect) }
    }

    @Test
    fun `bar shows the dot on play only when play is badged`() {
        assertBadge { badged -> TopLevelNavBar(selected = HomeKey, onSelect = {}, badged = badged) }
    }

    @Test
    fun `rail shows the dot on play only when play is badged`() {
        assertBadge { badged -> TopLevelNavRail(selected = HomeKey, onSelect = {}, badged = badged) }
    }

    private fun assertBadge(content: @Composable (Set<NavKey>) -> Unit) {
        var badged by mutableStateOf<Set<NavKey>>(emptySet())
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) { content(badged) }
        }
        assertDots(expected = emptySet())
        badged = setOf(PlayKey)
        composeRule.waitForIdle()
        assertDots(expected = setOf(PlayKey))
        val play = topLevelDestinations.single { it.key == PlayKey }
        composeRule.onNodeWithTag(play.tag, useUnmergedTree = true).assert(
            hasAnyDescendant(hasTestTag(badgeTag(play)) and hasContentDescription(IN_PROGRESS_BADGE_DESCRIPTION)),
        )
    }

    private fun assertDots(expected: Set<NavKey>) {
        composeRule.onAllNodes(hasContentDescription(IN_PROGRESS_BADGE_DESCRIPTION), useUnmergedTree = true)
            .assertCountEquals(expected.size)
        topLevelDestinations.forEach { destination ->
            composeRule.onAllNodesWithTag(badgeTag(destination), useUnmergedTree = true)
                .assertCountEquals(if (destination.key in expected) 1 else 0)
        }
    }

    private fun assertHighlight(content: @Composable (NavKey, (NavKey) -> Unit) -> Unit) {
        var selected by mutableStateOf<NavKey>(HomeKey)
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                content(selected) { selected = it }
            }
        }
        topLevelDestinations.forEach { target ->
            composeRule.onNodeWithTag(target.tag).performClick()
            composeRule.waitForIdle()
            assertEquals(target.key, selected)
            topLevelDestinations.forEach { destination ->
                composeRule.onAllNodesWithTag(destination.tag).assertCountEquals(1)
                val node = composeRule.onNodeWithTag(destination.tag)
                if (destination == target) node.assertIsSelected() else node.assertIsNotSelected()
            }
        }
    }
}
