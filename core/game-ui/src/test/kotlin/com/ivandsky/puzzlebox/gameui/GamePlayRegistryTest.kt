package com.ivandsky.puzzlebox.gameui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private open class FakePlayUi(override val gameId: GameId) : GamePlayUi {
    override fun createController(env: PlayEnvironment): GamePlayController = error("unused")

    override val tutorial: List<TutorialStep> get() = emptyList()

    @Composable
    override fun Board(controller: GamePlayController, modifier: Modifier) = Unit

    @Composable
    override fun Screen(
        controller: GamePlayController,
        status: PlayBoardStatus,
        layout: PlayLayout,
        onRetry: () -> Unit,
        onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
        modifier: Modifier,
    ) = Unit
}

/** A fake that registers its own brand; [FakePlayUi] itself keeps the default (neutral). */
private class BrandedPlayUi(gameId: GameId, override val brand: GameBrand) : FakePlayUi(gameId)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GamePlayRegistryTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `look up a game's play ui by id`() {
        val fake = FakePlayUi(GameId("fake"))
        val registry = GamePlayRegistry(listOf(fake))
        assertSame(fake, registry[GameId("fake")])
        assertNull(registry[GameId("missing")])
    }

    @Test
    fun `brandOf returns each registered brand and neutral for an unbranded or unknown id`() {
        val registry = GamePlayRegistry(
            listOf(
                BrandedPlayUi(GameId("blue"), GameBrand.Neutral.copy(accent = AccentSlot.Blue)),
                BrandedPlayUi(GameId("violet"), GameBrand.Neutral.copy(accent = AccentSlot.Violet)),
                FakePlayUi(GameId("plain")),
            ),
        )
        assertEquals(AccentSlot.Blue, registry.brandOf(GameId("blue")).accent)
        assertEquals(AccentSlot.Violet, registry.brandOf(GameId("violet")).accent)
        assertEquals(GameBrand.Neutral, registry.brandOf(GameId("plain")))
        assertEquals(GameBrand.Neutral, registry.brandOf(GameId("missing")))
    }

    @Test
    fun `a duplicate game id is a wiring bug and fails loudly`() {
        try {
            GamePlayRegistry(listOf(FakePlayUi(GameId("fake")), FakePlayUi(GameId("fake"))))
            assertTrue("expected IllegalArgumentException", false)
        } catch (expected: IllegalArgumentException) {
            // duplicates never silently overwrite
        }
    }

    @Test
    fun `loading status renders the loading state`() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                PlayBoardFallback(PlayBoardStatus.Loading, onRetry = {}, tagPrefix = "fake")
            }
        }
        composeRule.onNodeWithTag("fake.loading").assertExists()
        composeRule.onNodeWithText("Setting up the board…").assertExists()
    }

    @Test
    fun `unavailable status renders the error with a working retry`() {
        var retried = 0
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                PlayBoardFallback(PlayBoardStatus.Unavailable("offline"), onRetry = { retried++ }, tagPrefix = "fake")
            }
        }
        composeRule.onNodeWithTag("fake.error").assertExists()
        composeRule.onNodeWithText("offline").assertExists()
        composeRule.onNodeWithText("Try again").performClick()
        composeRule.runOnIdle { assertEquals(1, retried) }
    }

    @Test
    fun `a non-retryable unavailable status shows its copy without a retry`() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                PlayBoardFallback(
                    PlayBoardStatus.Unavailable("This puzzle isn't available.", retryable = false),
                    onRetry = {},
                    tagPrefix = "fake",
                )
            }
        }
        composeRule.onNodeWithTag("fake.error").assertExists()
        composeRule.onNodeWithText("This puzzle isn't available.").assertExists()
        composeRule.onNodeWithText("Try again").assertDoesNotExist()
    }
}
