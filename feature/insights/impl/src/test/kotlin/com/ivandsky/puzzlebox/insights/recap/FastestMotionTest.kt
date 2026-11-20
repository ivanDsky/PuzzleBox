package com.ivandsky.puzzlebox.insights.recap

import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.RecapCard as PaletteEntry
import com.ivandsky.puzzlebox.designsystem.ink
import com.ivandsky.puzzlebox.insights.GOLDEN_PHONE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The Fastest card's step to Tango (average 2:36, best 1:02) at fixed clock points after the tab tap, with motion on:
 * the card promotes up from under Queens, its timer climbs from 140 + 160ms (300-960), holds, the ghost average peels
 * off over 1040-1460 while the time drops (1040-1400), and after the 480ms pause the time stamps down (about 1880).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class FastestMotionTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /** The Fastest card's ink, as the theme resolves it. */
    private var ink = Color.Unspecified

    private fun showAndStep(animatorScale: Float) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, animatorScale)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            PuzzleBoxTheme {
                ink = PaletteEntry.Fastest.ink
                CompositionLocalProvider(LocalGameBrands provides FastestFixtures.brands) {
                    FastestCardPage(FastestFixtures.default, "October 2026", FastestFixtures.gameName, onWatchReplay = {})
                }
            }
        }
        rule.mainClock.advanceTimeBy(SETTLED.toLong())
        rule.onNodeWithTag("recap.fastest.tab.1").performClick()
        // The step's first frame is 0ms.
        rule.mainClock.advanceTimeByFrame()
    }

    private fun bounds(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    /** [tag] on the Tango card: Queens, leaving, keeps its nodes in the unmerged tree until it is gone. */
    private fun onTango(tag: String) =
        rule.onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag(TANGO)), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private class Frame(val games: PixelMap, val gamesAt: Rect, val card: Rect, val time: Rect, val ghost: Rect) {
        /** The time node is the timer's whole line box, which reaches up behind the ghost row: the digits are below it. */
        val digits get() = Rect(time.left, maxOf(time.top, ghost.bottom), time.right, time.bottom)
    }

    private fun frame() = Frame(
        rule.onNodeWithTag("recap.fastest.games").captureToImage().toPixelMap(),
        bounds("recap.fastest.games"),
        bounds(TANGO),
        onTango("recap.fastest.time"),
        onTango("recap.fastest.ghost"),
    )

    /** The pixels of [frame]'s games box inside [region] (root coordinates). */
    private fun crop(frame: Frame, region: Rect): IntArray {
        val left = (region.left - frame.gamesAt.left).toInt().coerceIn(0, frame.games.width)
        val top = (region.top - frame.gamesAt.top).toInt().coerceIn(0, frame.games.height)
        val right = (region.right - frame.gamesAt.left).toInt().coerceIn(left, frame.games.width)
        val bottom = (region.bottom - frame.gamesAt.top).toInt().coerceIn(top, frame.games.height)
        return IntArray((right - left) * (bottom - top)) { i -> frame.games.buffer[(top + i / (right - left)) * frame.games.width + left + i % (right - left)] }
    }

    private fun inkIn(pixels: IntArray): Int =
        pixels.count { argb ->
            val r = (argb shr 16 and 0xFF) / 255f
            val g = (argb shr 8 and 0xFF) / 255f
            val b = (argb and 0xFF) / 255f
            abs(r - ink.red) + abs(g - ink.green) + abs(b - ink.blue) < INK_TOLERANCE
        }

    @Test
    fun `after a step the card promotes in, the timer climbs, the ghost peels off and the time stamps after the pause`() {
        showAndStep(animatorScale = 1f)
        val at = linkedMapOf<Int, Frame>()
        var now = 0
        for (ms in listOf(0, 180, 560, 700, 1100, 1500)) {
            rule.mainClock.advanceTimeBy((ms - now).toLong())
            now = ms
            at[ms] = frame()
        }
        // The impact, frame by frame: the first frame the time is stamped up (scaled over its rest size).
        val restHeight = at.getValue(1500).time.height
        var stampedAt: Int? = null
        while (now < IMPACT_WINDOW_END) {
            rule.mainClock.advanceTimeByFrame()
            now += FRAME_MS
            if (stampedAt == null && onTango("recap.fastest.time").height > restHeight * STAMPED) stampedAt = now
        }
        rule.mainClock.advanceTimeBy((SETTLED - now).toLong())
        val settled = frame()
        val time = { ms: Int -> crop(at.getValue(ms), settled.digits) }
        val settledTime = crop(settled, settled.digits)

        // 0-180ms: the new card is there from the first frame, promoted up from its peek scale.
        val card0 = at.getValue(0).card.width
        assertEquals("the card starts at its peek scale", PEEK_SCALE, card0 / settled.card.width, 0.01f)
        assertTrue("the card grows to full size", at.getValue(180).card.width > card0)
        // 560-700ms: the timer is drawn full size and climbing; the ghost is not out yet.
        for (ms in listOf(560, 700)) {
            assertTrue("the timer has digits at ${ms}ms", inkIn(time(ms)) > inkIn(settledTime) * MIN_INK_SHARE)
        }
        assertFalse("the timer is rolling between 560 and 700ms", time(560).contentEquals(time(700)))
        assertEquals("no ghost before the peel", 0, inkIn(crop(at.getValue(700), settled.ghost)))
        // 1100ms: the ghost is mid-peel, smaller than the timer and bigger than its place, and the time is still dropping.
        val ghost = at.getValue(1100).ghost.width
        assertTrue("the ghost is shrinking ($ghost of ${settled.ghost.width})", ghost > settled.ghost.width * 1.05f && ghost < settled.ghost.width / GHOST_SCALE * 0.95f)
        assertFalse("the time has not landed at 1100ms", time(1100).contentEquals(settledTime))
        // 1500ms: the time has landed and the ghost is home; the stamp waits out the pause.
        assertTrue("the time has landed by 1500ms", time(1500).contentEquals(settledTime))
        assertEquals(settled.ghost, at.getValue(1500).ghost)
        val stamp = stampedAt
        assertTrue("the time stamps after the landing pause (at $stamp ms)", stamp != null && stamp >= EARLIEST_IMPACT)
    }

    @Test
    fun `with reduced motion a step crossfades straight to the struck ghost and the landed time`() {
        showAndStep(animatorScale = 0f)
        val first = frame()
        rule.mainClock.advanceTimeBy(CROSSFADE_DONE.toLong())
        val faded = frame()
        rule.mainClock.advanceTimeBy((SETTLED - CROSSFADE_DONE).toLong())
        val settled = frame()

        assertEquals("no promote: the card is full size at once", settled.card, first.card)
        assertEquals("the ghost is in place at once", settled.ghost, first.ghost)
        assertTrue("the time has landed once the crossfade is done", crop(faded, settled.digits).contentEquals(crop(settled, settled.digits)))
        assertTrue("the ghost is struck once the crossfade is done", crop(faded, settled.ghost).contentEquals(crop(settled, settled.ghost)))
    }
}

private const val TANGO = "recap.fastest.card.tango"
private const val SETTLED = 5_000
private const val FRAME_MS = 16
private const val IMPACT_WINDOW_END = 2_300

/** Drop end (1400) plus the landing pause (480), less a frame or two of slack. */
private const val EARLIEST_IMPACT = 1_840
private const val STAMPED = 1.05f
private const val PEEK_SCALE = 0.955f
private const val GHOST_SCALE = 0.42f
private const val MIN_INK_SHARE = 0.3f
private const val INK_TOLERANCE = 0.25f

/** The reduced crossfade (120ms) and a frame. */
private const val CROSSFADE_DONE = 140
