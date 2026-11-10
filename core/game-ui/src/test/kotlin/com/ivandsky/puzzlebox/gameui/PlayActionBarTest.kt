package com.ivandsky.puzzlebox.gameui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.colors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.MotionDurationScale

/** A14: the shared action bar forwards each press to the game, gates on its state, and hides Clear without a seam. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PlayActionBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val pressed = mutableListOf<String>()

    private fun setBar(onClear: (() -> Unit)?, canUndo: Boolean = true, hintEnabled: Boolean = true, hintsUsed: Int = 0) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                PlayActionBar(
                    onUndo = { pressed += "undo" },
                    onRedo = { pressed += "redo" },
                    onHint = { pressed += "hint" },
                    onClear = onClear,
                    canUndo = canUndo,
                    canRedo = true,
                    hintEnabled = hintEnabled,
                    accent = AccentSlot.Teal,
                    hintsUsed = hintsUsed,
                )
            }
        }
    }

    @Test
    fun `each press reaches the game`() {
        setBar(onClear = { pressed += "clear" })
        listOf("play.undo", "play.redo", "play.hint", "play.clear").forEach {
            composeRule.onNodeWithTag(it).performClick()
        }
        composeRule.waitForIdle()
        assertEquals(listOf("undo", "redo", "hint", "clear"), pressed)
    }

    @Test
    fun `no clear seam hides clear`() {
        setBar(onClear = null)
        composeRule.onNodeWithTag("play.clear").assertDoesNotExist()
        composeRule.onNodeWithTag("play.undo").assertExists()
    }

    @Test
    fun `disabled controls do not fire`() {
        setBar(onClear = null, canUndo = false, hintEnabled = false)
        composeRule.onNodeWithTag("play.undo").assertIsNotEnabled().performClick()
        composeRule.onNodeWithTag("play.hint").assertIsNotEnabled().performClick()
        composeRule.onNodeWithTag("play.redo").assertIsEnabled()
        composeRule.waitForIdle()
        assertEquals(emptyList<String>(), pressed)
    }

    @Test
    fun `used hints show their count`() {
        setBar(onClear = null, hintsUsed = 2)
        composeRule.onNode(hasTestTag("play.hint") and hasText("2", substring = true), useUnmergedTree = false)
            .assertExists()
    }

    @Test
    fun `the four actions share the row equally on a 64dp face`() {
        setBar(onClear = {})
        val tags = listOf("play.undo", "play.redo", "play.hint", "play.clear")
        val widths = tags.map { composeRule.onNodeWithTag(it).assertHeightIsAtLeast(64.dp).getUnclippedBoundsInRoot().width }
        assertTrue("action widths $widths", widths.max() - widths.min() < 1.dp)
    }

    // F2: the hint cooldown, on an injected clock. The compose rows pause the frame clock and
    // move it with [nowMs], so the fill animation and the cooldown's clock agree.

    private var nowMs = 0L

    private class Game : PlayHints, PlayUndo {
        var requests = 0
        override val hintsUsed = 0
        override var hintEnabled by mutableStateOf(true)
        override val hintCaption: String? = null
        override fun requestHint() {
            requests++
        }
        override val canUndo = false
        override val canRedo = false
        override fun undo() = Unit
        override fun redo() = Unit
    }

    private fun cooldown(window: Duration?, sweeps: Boolean = true) =
        HintCooldown(window, { nowMs }, mutableStateOf(null), sweeps)

    @Composable
    private fun GatedBar(game: Game, window: Duration? = HINT_COOLDOWN) {
        PuzzleBoxTheme(darkTheme = false) {
            CompositionLocalProvider(LocalHintCooldown provides window) {
                PlayActionBar(game, game, clear = null, accent = AccentSlot.Teal, modifier = Modifier, cooldown = rememberHintCooldown(game) { nowMs })
            }
        }
    }

    private fun advance(ms: Long) {
        nowMs += ms
        composeRule.mainClock.advanceTimeBy(ms)
    }

    private fun pressHint() {
        composeRule.onNodeWithTag("play.hint").performClick()
        advance(FRAME_MS)
    }

    @Test
    fun `a press waits out the cooldown - still waiting at 4_999 ms, free at 5_000`() {
        val gate = cooldown(HINT_COOLDOWN)
        var hints = 0
        gate.press { hints++ }
        assertFalse(gate.gate(enabled = true))
        nowMs = 4_999
        assertFalse(gate.gate(enabled = true))
        nowMs = 5_000
        assertTrue(gate.gate(enabled = true))
        assertEquals(1, hints)
    }

    @Test
    fun `a press inside the wait runs nothing`() {
        val gate = cooldown(HINT_COOLDOWN)
        var hints = 0
        gate.press { hints++ }
        nowMs = 4_999
        gate.press { hints++ }
        assertEquals(1, hints)
        nowMs = 5_000
        gate.press { hints++ }
        assertEquals(2, hints)
    }

    @Test
    fun `no cooldown never disables`() {
        val gate = cooldown(window = null)
        var hints = 0
        repeat(3) { gate.press { hints++ } }
        assertEquals(3, hints)
        assertTrue(gate.gate(enabled = true))
        assertNull(gate.ring)
    }

    @Test
    fun `the game's own disabled state wins after the wait`() {
        val gate = cooldown(HINT_COOLDOWN)
        gate.press {}
        nowMs = 5_000
        assertFalse(gate.gate(enabled = false))
    }

    @Test
    fun `the ring shows only while waiting, and never under reduced motion`() {
        val sweeping = cooldown(HINT_COOLDOWN)
        val still = cooldown(HINT_COOLDOWN, sweeps = false)
        assertNull(sweeping.ring)
        sweeping.press {}
        still.press {}
        assertNotNull(sweeping.ring)
        assertNull(still.ring)
        assertFalse(still.gate(enabled = true))
        nowMs = 5_000
        assertNull(sweeping.ring)
    }

    @Test
    fun `on screen a press disables Hint, presses during the wait reach nothing, then Hint returns`() {
        val game = Game()
        composeRule.setContent { GatedBar(game) }
        composeRule.mainClock.autoAdvance = false
        pressHint()
        composeRule.onNodeWithTag("play.hint").assertIsNotEnabled()
        composeRule.onNodeWithTag("play.hint").performClick()
        advance(4_000)
        composeRule.onNodeWithTag("play.hint").assertIsNotEnabled().performClick()
        assertEquals(1, game.requests)
        advance(1_100)
        composeRule.onNodeWithTag("play.hint").assertIsEnabled()
        pressHint()
        assertEquals(2, game.requests)
    }

    @Test
    fun `without the cooldown local Hint never disables`() {
        val game = Game()
        composeRule.setContent { PuzzleBoxTheme(darkTheme = false) { PlayActionBar(game, game, clear = null, accent = AccentSlot.Teal) } }
        repeat(3) {
            composeRule.onNodeWithTag("play.hint").assertIsEnabled().performClick()
        }
        composeRule.waitForIdle()
        assertEquals(3, game.requests)
    }

    @Test
    fun `a new board starts with no cooldown`() {
        var game by mutableStateOf(Game())
        composeRule.setContent { GatedBar(game) }
        composeRule.mainClock.autoAdvance = false
        pressHint()
        composeRule.onNodeWithTag("play.hint").assertIsNotEnabled()
        game = Game()
        advance(FRAME_MS)
        composeRule.onNodeWithTag("play.hint").assertIsEnabled()
    }

    @Test
    fun `the wait survives a configuration change`() {
        val game = Game()
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { GatedBar(game) }
        composeRule.mainClock.autoAdvance = false
        pressHint()
        advance(2_000)
        restoration.emulateSavedInstanceStateRestore()
        advance(FRAME_MS)
        composeRule.onNodeWithTag("play.hint").assertIsNotEnabled()
        advance(3_100)
        composeRule.onNodeWithTag("play.hint").assertIsEnabled()
    }

    @Test
    fun `halfway through the wait the ring has traced the right side but not the left`() {
        val game = Game()
        var accent = Color.Unspecified
        composeRule.setContent {
            GatedBar(game)
            PuzzleBoxTheme(darkTheme = false) { accent = AccentSlot.Teal.colors().accent }
        }
        composeRule.mainClock.autoAdvance = false
        pressHint()
        advance(2_500)
        val pixels = composeRule.onNodeWithTag("play.hint").captureToImage().toPixelMap()
        val inset = with(composeRule.density) { 2.dp.roundToPx() }
        val middle = pixels.height / 2
        val right = pixels[pixels.width - 1 - inset, middle]
        val left = pixels[inset, middle]
        assertTrue("right edge $right vs $accent", near(accent, right))
        assertFalse("left edge $left vs $accent", near(accent, left))
    }

    private fun near(a: Color, b: Color) =
        abs(a.red - b.red) < 0.02f && abs(a.green - b.green) < 0.02f && abs(a.blue - b.blue) < 0.02f

    @Test
    fun `at half animator speed the wait still ends at 5 s, not 2_5`() = waitEndsOnTheClock(animatorScale = 0.5f)

    @Test
    fun `at ten-times animator duration the wait still ends at 5 s, not 50`() = waitEndsOnTheClock(animatorScale = 10f)

    /** Runs one wait under [animatorScale] on virtual time: the button frees when the clock says, whatever the sweep's scale. */
    private fun waitEndsOnTheClock(animatorScale: Float) = runTest {
        val gate = HintCooldown(HINT_COOLDOWN, { testScheduler.currentTime }, mutableStateOf(null), sweeps = true)
        val scale = object : MotionDurationScale {
            override val scaleFactor = animatorScale
        }
        gate.press {}
        withContext(VirtualFrameClock(testScheduler) + scale) { gate.runOut() }
        assertEquals(HINT_COOLDOWN.inWholeMilliseconds, testScheduler.currentTime)
        assertNull(gate.pressedAt)
    }

    @Test
    fun `a second press starts its ring empty, before any frame advances`() = runTest {
        val gate = HintCooldown(HINT_COOLDOWN, { testScheduler.currentTime }, mutableStateOf(null), sweeps = true)
        gate.press {}
        withContext(VirtualFrameClock(testScheduler)) { gate.runOut() }
        gate.press {}
        assertEquals(0f, gate.ring?.invoke())
    }

    private class VirtualFrameClock(private val scheduler: TestCoroutineScheduler) : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            delay(FRAME_MS)
            return onFrame(scheduler.currentTime * 1_000_000)
        }
    }

    private companion object {
        const val FRAME_MS = 16L
    }
}
