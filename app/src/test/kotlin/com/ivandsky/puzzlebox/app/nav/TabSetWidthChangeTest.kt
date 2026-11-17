package com.ivandsky.puzzlebox.app.nav

import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.PuzzleBoxApp
import com.ivandsky.puzzlebox.app.e2e.E2E_TODAY
import com.ivandsky.puzzlebox.app.e2e.E2eKoinGraph
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.navigation.Navigator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A window resize mid-session (rotation, fold, split screen) swaps the bar for the rail and
 * back while the selected tab, every stack and the highlight stay as they were. The rail needs
 * width, landscape and 480 dp of height, so a phone in landscape keeps the bar.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w1280dp-h800dp-land")
class TabSetWidthChangeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val graph = E2eKoinGraph(E2E_TODAY)

    @Before
    fun startGraph() = graph.start()

    @After
    fun stopGraph() = graph.stop()

    @Test
    fun `a resize across the two-pane boundary keeps History selected and its stack, with four tabs in bar and rail`() {
        val inHistory = puzzleBoxNavigationState().selectTab(HistoryRootKey).push(HistoryKey("zip"))
        var window by mutableStateOf(DpSize(891.dp, 448.dp))
        lateinit var navigator: Navigator
        composeRule.setContent {
            val density = LocalDensity.current
            val info = remember(density) {
                object : WindowInfo {
                    override val isWindowFocused = true
                    override val containerSize get() = with(density) {
                        IntSize(window.width.roundToPx(), window.height.roundToPx())
                    }
                }
            }
            CompositionLocalProvider(LocalWindowInfo provides info) {
                PuzzleBoxTheme(darkTheme = false) {
                    PuzzleBoxApp(
                        windowWidthSizeClass = WindowWidthSizeClass.Expanded,
                        navigator = rememberNavigator(inHistory).also { navigator = it },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(inHistory, composeRule.runOnIdle { navigator.state })
        assertTabs(rail = false)

        window = DpSize(1280.dp, 800.dp)
        composeRule.waitForIdle()
        assertEquals(inHistory, composeRule.runOnIdle { navigator.state })
        assertTabs(rail = true)

        window = DpSize(891.dp, 448.dp)
        composeRule.waitForIdle()
        assertEquals(inHistory, composeRule.runOnIdle { navigator.state })
        assertTabs(rail = false)
    }

    /**
     * The four tabs show once each, no Profile tab, only History highlighted; in one
     * row along the bottom for the bar, in one column at the start for the [rail].
     */
    private fun assertTabs(rail: Boolean) {
        val tabs = topLevelDestinations
        val bounds = tabs.map { destination ->
            composeRule.onAllNodesWithTag(destination.tag).fetchSemanticsNodes().let { nodes ->
                assertEquals("tab ${destination.label} shown once", 1, nodes.size)
            }
            val node = composeRule.onNodeWithTag(destination.tag)
            if (destination.key == HistoryRootKey) node.assertIsSelected() else node.assertIsNotSelected()
            node.fetchSemanticsNode().boundsInRoot
        }
        assertEquals(0, composeRule.onAllNodesWithTag("nav.tab.profile").fetchSemanticsNodes().size)
        if (rail) {
            assertTrue("rail tabs share one column: $bounds", bounds.sameEdge(Rect::left))
        } else {
            assertTrue("bar tabs share one row: $bounds", bounds.sameEdge(Rect::top))
        }
    }

    private fun List<Rect>.sameEdge(edge: (Rect) -> Float): Boolean = all { edge(it) == edge(first()) }
}
