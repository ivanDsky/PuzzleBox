package com.ivandsky.puzzlebox.insights.recap

import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.colors
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
 * The story's step from Queens' Fastest page to Tango's (average 2:36, best 1:02) at fixed clock points after the step,
 * with motion on: Tango's page is under the throw at full size from the first frame, its timer climbs from 140 + 160ms
 * (300-960), holds, the ghost average peels off over 1040-1460 while the time drops (1040-1400), and after the 480ms
 * pause the time stamps down (about 1880). Each digit rolls in its own window, the colon stays put.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class FastestMotionTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /** Tango's on-accent ink, as the theme resolves it. */
    private var ink = Color.Unspecified

    private fun showAndStep(animatorScale: Float) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, animatorScale)
        rule.mainClock.autoAdvance = false
        val story = FastestFixtures.story(FastestFixtures.default)
        // The Fastest card is the story's second card: its first page, Queens, is page 1.
        val deck = RecapDeckState(pagesOf(story.cards, share = false), initialPage = 1).apply { dealt() }
        rule.setContent {
            PuzzleBoxTheme {
                ink = AccentSlot.Orange.colors().onAccent
                CompositionLocalProvider(LocalGameBrands provides FastestFixtures.brands) {
                    RecapStory(story.cards, deck, story.period, story.title, FastestFixtures.gameName, onWatchReplay = {}, onClose = {})
                }
            }
        }
        rule.mainClock.advanceTimeBy(SETTLED.toLong())
        // The step is written from the test thread: tell the recomposer now, so the next frame is the step's first.
        rule.runOnIdle {
            deck.forward()
            Snapshot.sendApplyNotifications()
        }
        // The step's first frame is 0ms.
        rule.mainClock.advanceTimeByFrame()
    }

    /** [tag] on Tango's page: Queens, thrown off, clears its nodes while it flies. */
    private fun onTango(tag: String) =
        rule.onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag(TANGO)), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private class Frame(val page: PixelMap, val card: Rect, val time: Rect, val ghost: Rect)

    private fun frame() = Frame(
        rule.onNodeWithTag(TANGO, useUnmergedTree = true).captureToImage().toPixelMap(),
        rule.onNodeWithTag(TANGO, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot,
        onTango("recap.fastest.time"),
        onTango("recap.fastest.ghost"),
    )

    /** The pixels of [frame]'s page inside [region] (root coordinates), row by row. */
    private fun crop(frame: Frame, region: Rect): Crop {
        val left = (region.left - frame.card.left).toInt().coerceIn(0, frame.page.width)
        val top = (region.top - frame.card.top).toInt().coerceIn(0, frame.page.height)
        val right = (region.right - frame.card.left).toInt().coerceIn(left, frame.page.width)
        val bottom = (region.bottom - frame.card.top).toInt().coerceIn(top, frame.page.height)
        val width = right - left
        return Crop(width, IntArray(width * (bottom - top)) { i -> frame.page.buffer[(top + i / width) * frame.page.width + left + i % width] })
    }

    private class Crop(val width: Int, val pixels: IntArray) {
        val height get() = if (width == 0) 0 else pixels.size / width
        fun contentEquals(other: Crop) = width == other.width && pixels.contentEquals(other.pixels)
    }

    private fun isInk(argb: Int): Boolean {
        val r = (argb shr 16 and 0xFF) / 255f
        val g = (argb shr 8 and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return abs(r - ink.red) + abs(g - ink.green) + abs(b - ink.blue) < INK_TOLERANCE
    }

    private fun inkIn(crop: Crop): Int = crop.pixels.count(::isInk)

    /** The rows of [crop] with ink in them. */
    private fun inkedRows(crop: Crop): List<Int> = (0 until crop.height).filter { y -> (0 until crop.width).any { x -> isInk(crop.pixels[y * crop.width + x]) } }

    /** The runs of [crop]'s columns with ink in them, left to right, as x ranges. */
    private fun inkRuns(crop: Crop): List<IntRange> {
        val inked = (0 until crop.width).map { x -> (0 until crop.height).any { y -> isInk(crop.pixels[y * crop.width + x]) } }
        val runs = mutableListOf<IntRange>()
        var start = -1
        inked.forEachIndexed { x, on ->
            if (on && start < 0) start = x
            if (!on && start >= 0) {
                runs += start until x
                start = -1
            }
        }
        if (start >= 0) runs += start until crop.width
        return runs
    }

    @Test
    fun `after a step the next game's page is there, the timer climbs, the ghost peels off and the time stamps after the pause`() {
        showAndStep(animatorScale = 1f)
        val at = linkedMapOf<Int, Frame>()
        var now = 0
        for (ms in listOf(0, 560, 700, 1100, 1500)) {
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
        val time = { ms: Int -> crop(at.getValue(ms), settled.time) }
        val settledTime = crop(settled, settled.time)

        // 0ms: the deck fades the page up in place under the throw, full size from its first frame.
        assertEquals("the page is full size from the first frame", settled.card, at.getValue(0).card)
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

        // The odometer: each digit sits inside its window, one digit tall, clear of its top and bottom edge.
        val rows = inkedRows(settledTime)
        assertTrue("the digits clear the window's top and bottom rows", rows.isNotEmpty() && rows.first() > 0 && rows.last() < settledTime.height - 1)
        assertTrue("the digits fill the window (${rows.size} of ${settledTime.height} rows)", rows.last() - rows.first() + 1 > settledTime.height * MIN_DIGIT_SPAN)
        // 1:02 reads digit, colon, digit, digit: the colon is the second run of ink, and it never moves while the digits
        // roll. 1100ms is left out: the ghost peels off the timer then and passes over it.
        val colon = inkRuns(settledTime)[1]
        val strip = { ms: Int -> time(ms).let { c -> (0 until c.height).flatMap { y -> colon.map { x -> c.pixels[y * c.width + x] } } } }
        for (ms in listOf(700, 1500)) {
            assertEquals("the colon holds still at ${ms}ms", strip(560), strip(ms))
        }
    }

    @Test
    fun `with reduced motion a step crossfades straight to the struck ghost and the landed time`() {
        showAndStep(animatorScale = 0f)
        val first = frame()
        rule.mainClock.advanceTimeBy(CROSSFADE_DONE.toLong())
        val faded = frame()
        rule.mainClock.advanceTimeBy((SETTLED - CROSSFADE_DONE).toLong())
        val settled = frame()

        assertEquals("the page is full size at once", settled.card, first.card)
        assertEquals("the ghost is in place at once", settled.ghost, first.ghost)
        assertTrue("the time has landed once the crossfade is done", crop(faded, settled.time).contentEquals(crop(settled, settled.time)))
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
private const val GHOST_SCALE = 0.42f
private const val MIN_INK_SHARE = 0.3f
private const val INK_TOLERANCE = 0.25f

/** A digit's ink against its 0.70em window: about 0.64em of it. */
private const val MIN_DIGIT_SPAN = 0.8f

/** The reduced crossfade (120ms) and a frame. */
private const val CROSSFADE_DONE = 140
