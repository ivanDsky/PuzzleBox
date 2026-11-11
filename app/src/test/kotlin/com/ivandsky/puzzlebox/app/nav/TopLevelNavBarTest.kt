package com.ivandsky.puzzlebox.app.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
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
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
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
 * shows the described dot on its own item only, and never while that item is selected.
 * The flag-off set is the default; the flag-on rows pass `topLevelDestinations(navV2 = true)`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TopLevelNavBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `tabs are home, play, create, profile in that order`() {
        assertEquals(
            listOf("nav.tab.home", "nav.tab.play", "nav.tab.create", "nav.tab.profile"),
            topLevelDestinations.map { it.tag },
        )
    }

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

    @Test
    fun `bar hides the dot while play is selected`() {
        assertBadgeHiddenWhenSelected { selected -> TopLevelNavBar(selected = selected, onSelect = {}, badged = setOf(PlayKey)) }
    }

    @Test
    fun `rail hides the dot while play is selected`() {
        assertBadgeHiddenWhenSelected { selected -> TopLevelNavRail(selected = selected, onSelect = {}, badged = setOf(PlayKey)) }
    }

    @Test
    fun `flag-on tabs are today, games, history, create in that order`() {
        val flagOn = topLevelDestinations(navV2 = true)

        assertEquals(listOf("Today", "Games", "History", "Create"), flagOn.map { it.label })
        assertEquals(
            listOf("nav.tab.home", "nav.tab.play", "nav.tab.history", "nav.tab.create"),
            flagOn.map { it.tag },
        )
        assertEquals(listOf(HomeKey, PlayKey, HistoryRootKey, CreateKey), flagOn.map { it.key })
        assertEquals(listOf(Tab.Today, Tab.Games, Tab.History, Tab.Create), flagOn.map { it.tab })
    }

    @Test
    fun `flag-on bar shows four tabs, no profile, and highlights exactly the selected one`() {
        val flagOn = topLevelDestinations(navV2 = true)
        assertHighlight(flagOn) { selected, onSelect ->
            TopLevelNavBar(selected = selected, onSelect = onSelect, destinations = flagOn)
        }
        assertOnlyTabs(flagOn)
    }

    @Test
    fun `flag-on rail shows four tabs, no profile, and highlights exactly the selected one`() {
        val flagOn = topLevelDestinations(navV2 = true)
        assertHighlight(flagOn) { selected, onSelect ->
            TopLevelNavRail(selected = selected, onSelect = onSelect, destinations = flagOn)
        }
        assertOnlyTabs(flagOn)
    }

    @Test
    fun `flag-on bar shows the dot on games only`() {
        val flagOn = topLevelDestinations(navV2 = true)
        assertBadge(flagOn) { badged -> TopLevelNavBar(selected = HomeKey, onSelect = {}, badged = badged, destinations = flagOn) }
    }

    @Test
    fun `flag-on rail shows the dot on games only`() {
        val flagOn = topLevelDestinations(navV2 = true)
        assertBadge(flagOn) { badged -> TopLevelNavRail(selected = HomeKey, onSelect = {}, badged = badged, destinations = flagOn) }
    }

    @Test
    fun `flag-on rail with an avatar slot is 96dp wide`() {
        val flagOn = topLevelDestinations(navV2 = true)
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.height(900.dp)) {
                    TopLevelNavRail(
                        selected = HomeKey,
                        onSelect = {},
                        modifier = Modifier.testTag("test.rail"),
                        destinations = flagOn,
                        bottomActions = { Box(Modifier.size(48.dp)) },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("test.rail").assertWidthIsEqualTo(96.dp)
    }

    @Test
    fun `flag-on rail draws 28dp tab icons`() {
        val flagOn = topLevelDestinations(navV2 = true)
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.height(900.dp)) {
                    TopLevelNavRail(selected = HomeKey, onSelect = {}, destinations = flagOn, bottomActions = { Box(Modifier.size(48.dp)) })
                }
            }
        }

        flagOn.forEach { destination ->
            composeRule.onNodeWithTag(iconTag(destination), useUnmergedTree = true).assertWidthIsEqualTo(28.dp).assertHeightIsEqualTo(28.dp)
        }
    }

    @Test
    fun `flag-off rail keeps 24dp tab icons`() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) { TopLevelNavRail(selected = HomeKey, onSelect = {}) }
        }

        topLevelDestinations.forEach { destination ->
            composeRule.onNodeWithTag(iconTag(destination), useUnmergedTree = true).assertWidthIsEqualTo(24.dp).assertHeightIsEqualTo(24.dp)
        }
    }

    /** The bar or rail shows exactly [destinations]' items, and no flag-off Profile item. */
    private fun assertOnlyTabs(destinations: List<TopLevelDestination>) {
        val tabItem = SemanticsMatcher("a nav.tab.<name> item") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.matches(Regex("nav\\.tab\\.[a-z]+")) == true
        }
        composeRule.onAllNodes(tabItem).assertCountEquals(destinations.size)
        composeRule.onAllNodesWithTag("nav.tab.profile").assertCountEquals(0)
    }

    private fun assertBadgeHiddenWhenSelected(content: @Composable (NavKey) -> Unit) {
        var selected by mutableStateOf<NavKey>(PlayKey)
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) { content(selected) }
        }
        assertDots(expected = emptySet())
        selected = HomeKey
        composeRule.waitForIdle()
        assertDots(expected = setOf(PlayKey))
    }

    private fun assertBadge(
        destinations: List<TopLevelDestination> = topLevelDestinations,
        content: @Composable (Set<NavKey>) -> Unit,
    ) {
        var badged by mutableStateOf<Set<NavKey>>(emptySet())
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) { content(badged) }
        }
        assertDots(expected = emptySet(), destinations)
        badged = setOf(PlayKey)
        composeRule.waitForIdle()
        assertDots(expected = setOf(PlayKey), destinations)
        val play = destinations.single { it.key == PlayKey }
        composeRule.onNodeWithTag(play.tag, useUnmergedTree = true).assert(
            hasAnyDescendant(hasTestTag(badgeTag(play)) and hasContentDescription(IN_PROGRESS_BADGE_DESCRIPTION)),
        )
    }

    private fun assertDots(expected: Set<NavKey>, destinations: List<TopLevelDestination> = topLevelDestinations) {
        composeRule.onAllNodes(hasContentDescription(IN_PROGRESS_BADGE_DESCRIPTION), useUnmergedTree = true)
            .assertCountEquals(expected.size)
        destinations.forEach { destination ->
            composeRule.onAllNodesWithTag(badgeTag(destination), useUnmergedTree = true)
                .assertCountEquals(if (destination.key in expected) 1 else 0)
        }
    }

    private fun assertHighlight(
        destinations: List<TopLevelDestination> = topLevelDestinations,
        content: @Composable (NavKey, (NavKey) -> Unit) -> Unit,
    ) {
        var selected by mutableStateOf<NavKey>(HomeKey)
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                content(selected) { selected = it }
            }
        }
        destinations.forEach { target ->
            composeRule.onNodeWithTag(target.tag).performClick()
            composeRule.waitForIdle()
            assertEquals(target.key, selected)
            destinations.forEach { destination ->
                composeRule.onAllNodesWithTag(destination.tag).assertCountEquals(1)
                val node = composeRule.onNodeWithTag(destination.tag)
                if (destination == target) node.assertIsSelected() else node.assertIsNotSelected()
            }
        }
    }
}
