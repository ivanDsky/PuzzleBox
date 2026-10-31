package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.GameId
import com.ivandsky.puzzlebox.game.RendererModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private class FakeModel(val label: String) : RendererModel

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GameUiRegistryTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `register then look up by game id`() {
        val registry = GameUiRegistry()
        val renderer = GameBoardRenderer { model, _ ->
            Text((model as FakeModel).label)
        }
        registry.register(GameId("fake"), renderer)
        assertEquals(renderer, registry[GameId("fake")])
        assertNull(registry[GameId("missing")])
    }

    @Test
    fun `double registration is a wiring bug and fails loudly`() {
        val registry = GameUiRegistry()
        val renderer = GameBoardRenderer { _, _ -> }
        registry.register(GameId("fake"), renderer)
        try {
            registry.register(GameId("fake"), renderer)
            assertTrue("expected IllegalStateException", false)
        } catch (expected: IllegalStateException) {
            // first registration wins; duplicates never silently overwrite
        }
    }

    @Test
    fun `board renders through the registry mapping`() {
        val registry = GameUiRegistry()
        registry.register(GameId("fake")) { model, modifier ->
            Text((model as FakeModel).label, modifier = modifier)
        }
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                registry.Board(GameId("fake"), FakeModel("fake board"), Modifier.requiredSize(100.dp))
            }
        }
        composeRule.onNodeWithText("fake board").assertExists()
    }

    @Test
    fun `null model while loading renders the loading state`() {
        val registry = GameUiRegistry()
        registry.register(GameId("fake")) { _, _ -> }
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                registry.Board(GameId("fake"), null, Modifier.requiredSize(100.dp))
            }
        }
        composeRule.onNodeWithText("Setting up the board…").assertExists()
    }

    @Test
    fun `unregistered game id fails loudly`() {
        val registry = GameUiRegistry()
        try {
            composeRule.setContent {
                registry.Board(GameId("ghost"), FakeModel("x"), Modifier.requiredSize(10.dp))
            }
            composeRule.waitForIdle()
            assertTrue("expected IllegalStateException", false)
        } catch (expected: IllegalStateException) {
            // no silent blank boards
        }
    }
}
