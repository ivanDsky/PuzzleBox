package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The shell-actions slot: both top bars draw what the app provides after their own
// actions, and nothing when nothing is provided.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ShellActionsTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Both bars in one composition; each bar's actions carry the bar's name in their tag.
    private val bars: List<Pair<String, @Composable (@Composable RowScope.() -> Unit) -> Unit>> = listOf(
        "large" to { actions -> LargeTopBar(title = "Title", actions = actions) },
        "compact" to { actions -> CompactTopBar(title = "Title", actions = actions) },
    )

    private fun setBars(shellAction: (@Composable RowScope.(String) -> Unit)?) {
        composeRule.setContent {
            PuzzleBoxTheme {
                Column {
                    bars.forEach { (name, bar) ->
                        val screen: @Composable RowScope.() -> Unit = {
                            Box(Modifier.size(48.dp).testTag("$name.screen"))
                        }
                        if (shellAction == null) {
                            bar(screen)
                        } else {
                            CompositionLocalProvider(
                                LocalShellActions provides { shellAction(name) },
                            ) { bar(screen) }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `a provided shell action draws after the bar's own actions`() {
        setBars { name -> Box(Modifier.size(48.dp).testTag("$name.shell")) }
        bars.forEach { (name, _) ->
            val screenRight = composeRule.onNodeWithTag("$name.screen").getBoundsInRoot().right
            val shellLeft = composeRule.onNodeWithTag("$name.shell").getBoundsInRoot().left
            assertTrue("$name: shell action at $shellLeft starts before screen action ends at $screenRight", shellLeft >= screenRight)
        }
    }

    @Test
    fun `a bar draws no extra action when nothing is provided`() {
        setBars(null)
        bars.forEach { (name, _) ->
            composeRule.onAllNodesWithTag("$name.screen").assertCountEquals(1)
            composeRule.onAllNodesWithTag("$name.shell").assertCountEquals(0)
        }
    }
}
