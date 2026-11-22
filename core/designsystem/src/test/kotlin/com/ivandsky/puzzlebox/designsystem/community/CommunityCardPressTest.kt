package com.ivandsky.puzzlebox.designsystem.community

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.down
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The press language of the community cards: .97 for shelf, entry and make cards, .985 for the
// spotlight, reached inside 120ms and released the same way; reduced motion never scales.
// Sampled on a paused test clock from the card's root bounds, where the layer scale shows.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class CommunityCardPressTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(reducedMotion: Boolean = false, card: @Composable (Modifier) -> Unit) {
        if (reducedMotion) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { PuzzleBoxTheme { Box { card(Modifier.testTag(CARD)) } } }
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun scale(restWidthDp: Float): Float =
        composeRule.onNodeWithTag(CARD).getBoundsInRoot().width.value / restWidthDp

    private fun press() = composeRule.onNodeWithTag(CARD).performTouchInput { down(center) }

    private fun release() = composeRule.onNodeWithTag(CARD).performTouchInput { up() }

    private val shelf: @Composable (Modifier) -> Unit = {
        ShelfCard(
            title = "Spiral staircase",
            author = "Mia",
            solves = 132,
            accent = AccentSlot.Blue,
            glyph = GameGlyphs.Zip,
            onClick = {},
            modifier = it,
        )
    }

    private val spotlight: @Composable (Modifier) -> Unit = {
        SpotlightCard(
            title = "Spiral staircase",
            gameName = "Zip",
            author = "Mia",
            solves = 4120,
            accent = AccentSlot.Blue,
            glyph = GameGlyphs.Zip,
            onClick = {},
            modifier = it,
        )
    }

    private val entry: @Composable (Modifier) -> Unit = {
        EntryTile(title = "From friends", subtitle = "7 puzzles", onClick = {}, modifier = it, leading = {})
    }

    private val make: @Composable (Modifier) -> Unit = {
        MakePuzzleCard(gameName = "Zip", onClick = {}, modifier = it)
    }

    @Test
    fun `a held press scales a shelf card to 0_97 within 120ms`() {
        show(card = shelf)
        press()
        composeRule.mainClock.advanceTimeBy(ShelfCardDefaults.PressMillis.toLong())
        assertEquals(0.97f, scale(156f), TOLERANCE)
    }

    @Test
    fun `release brings a shelf card back to full size within 120ms`() {
        show(card = shelf)
        press()
        composeRule.mainClock.advanceTimeBy(ShelfCardDefaults.PressMillis.toLong())
        release()
        composeRule.mainClock.advanceTimeBy(ShelfCardDefaults.PressMillis.toLong())
        assertEquals(1f, scale(156f), TOLERANCE)
    }

    @Test
    fun `a held press scales the spotlight to 0_985`() {
        show(card = spotlight)
        val rest = composeRule.onNodeWithTag(CARD).getBoundsInRoot().width.value
        press()
        composeRule.mainClock.advanceTimeBy(ShelfCardDefaults.PressMillis.toLong())
        assertEquals(0.985f, scale(rest), TOLERANCE)
    }

    @Test
    fun `a held press scales an entry tile and a make card to 0_97`() {
        show(card = entry)
        val entryRest = composeRule.onNodeWithTag(CARD).getBoundsInRoot().width.value
        press()
        composeRule.mainClock.advanceTimeBy(ShelfCardDefaults.PressMillis.toLong())
        assertEquals(0.97f, scale(entryRest), TOLERANCE)
    }

    @Test
    fun `a held press scales a make card to 0_97`() {
        show(card = make)
        press()
        composeRule.mainClock.advanceTimeBy(ShelfCardDefaults.PressMillis.toLong())
        assertEquals(0.97f, scale(132f), TOLERANCE)
    }

    @Test
    fun `reduced motion never scales a shelf card`() {
        show(reducedMotion = true, card = shelf)
        press()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(1f, scale(156f), TOLERANCE)
    }

    @Test
    fun `reduced motion never scales the spotlight`() {
        show(reducedMotion = true, card = spotlight)
        val rest = composeRule.onNodeWithTag(CARD).getBoundsInRoot().width.value
        press()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(1f, scale(rest), TOLERANCE)
    }

    private fun pixels() = composeRule.onNodeWithTag(CARD).captureToImage().let { image ->
        IntArray(image.width * image.height).also { image.readPixels(it) }
    }

    @Test
    fun `reduced motion washes a pressed shelf card instead of scaling it`() {
        show(reducedMotion = true, card = shelf)
        val rest = pixels()
        press()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        assertFalse("the press shows as a wash", rest.contentEquals(pixels()))
    }

    private companion object {
        const val CARD = "card"
        const val TOLERANCE = 0.004f
    }
}
