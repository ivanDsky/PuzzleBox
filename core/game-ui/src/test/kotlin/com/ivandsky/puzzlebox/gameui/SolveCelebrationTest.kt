package com.ivandsky.puzzlebox.gameui

import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.moments.Moment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * L2: the celebration overlay ends after 1 s, 2 s with a moment, or at once on a tap, and only once. Its stamp lands
 * on a spring once Result is in, with one success buzz and a burst; a moment gets a second burst a second in.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SolveCelebrationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var moment by mutableStateOf(false)
    private var dones = 0
    private lateinit var view: View

    /** What went through Compose's haptics: the moment buzz. The success buzz goes through the view. */
    private val momentBuzzes = mutableListOf<HapticFeedbackType>()
    private val composeHaptics = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            momentBuzzes += hapticFeedbackType
        }
    }

    /** Clock time since the overlay was set, ms. */
    private var now = 0L

    @After
    fun restoreMotion() = setAnimatorScale(1f)

    private fun setAnimatorScale(scale: Float) {
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    private fun setOverlay(strongest: Moment? = null) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalHapticFeedback provides composeHaptics) {
                PuzzleBoxTheme(darkTheme = false) {
                    ResultMomentHapticsEffect(strongest)
                    SolveCelebration(brand = GameBrand.Neutral, moment = moment, onDone = { dones++ })
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(16)
        now = 16
    }

    /** Runs the clock on to [ms] after the overlay was set. */
    private fun runTo(ms: Long) {
        composeRule.mainClock.advanceTimeBy(ms - now)
        now = ms
    }

    private fun stamp() = composeRule.onNodeWithTag("solve.celebration_stamp", useUnmergedTree = true)

    private fun stampWidth(): Float = stamp().fetchSemanticsNode().boundsInRoot.width

    private fun burst(index: Int) = composeRule.onNodeWithTag("solve.celebration_burst.$index", useUnmergedTree = true)

    /** What the overlay draws this frame, as a hash of its pixels. */
    private fun drawn(): Int =
        composeRule.onNodeWithTag("solve.celebration").captureToImage().toPixelMap().buffer.contentHashCode()

    private fun lastBuzz(): Int = shadowOf(view).lastHapticFeedbackPerformed()

    @Test
    fun `the stamp lands once Result is in, small at first, and comes to rest larger than the board's`() {
        setOverlay()
        runTo(LAND_MS - 64)
        stamp().assertDoesNotExist()

        runTo(LAND_MS + 32)
        val landing = stampWidth()
        runTo(REST_MS)
        val rest = stampWidth()

        assertTrue("the stamp grows as it lands: $landing px at the landing, $rest px at rest", landing < rest)
        val boardStamp = with(composeRule.density) { GameGlyphSize.Hero.box.toPx() }
        assertTrue("the stamp at rest ($rest px) is larger than the board's ($boardStamp px)", rest > boardStamp)
    }

    @Test
    fun `a burst flies from the landing and is over before the celebration ends`() {
        setOverlay()
        runTo(LAND_MS - 64)
        burst(0).assertDoesNotExist()

        runTo(LAND_MS + 320)
        burst(0).assertExists()
        val midBurst = drawn()

        runTo(REST_MS)
        burst(0).assertDoesNotExist()
        burst(1).assertDoesNotExist()
        assertNotEquals("the burst draws something of its own", drawn(), midBurst)
        assertEquals("still playing", 0, dones)
    }

    @Test
    fun `a moment gets a second burst a second in`() {
        moment = true
        setOverlay()
        runTo(REST_MS)
        burst(1).assertDoesNotExist()

        runTo(SECOND_BURST_MS + 64)
        burst(1).assertExists()
        burst(0).assertDoesNotExist()

        runTo(1_900)
        burst(1).assertDoesNotExist()
        assertEquals("still playing", 0, dones)
    }

    @Test
    fun `reduced motion draws the stamp at rest from the first frame, with no burst, moment or not`() {
        setAnimatorScale(0f)
        moment = true
        setOverlay()
        val first = drawn()
        val firstWidth = stampWidth()
        burst(0).assertDoesNotExist()

        runTo(LAND_MS + 320)
        burst(0).assertDoesNotExist()
        assertEquals("nothing moves", first, drawn())
        assertEquals("the stamp never scales", firstWidth, stampWidth())

        runTo(SECOND_BURST_MS + 64)
        burst(1).assertDoesNotExist()
        assertEquals("nothing moves for a moment either", first, drawn())
    }

    @Test
    fun `one success buzz as the stamp lands, none before and none after, and a moment adds its own`() {
        moment = true
        setOverlay(strongest = Moment.PersonalBest)
        runTo(LAND_MS - 64)
        assertEquals("no buzz before the stamp lands", NO_BUZZ, lastBuzz())

        runTo(LAND_MS + 32)
        assertEquals("the success buzz at the landing", HapticFeedbackConstants.CONFIRM, lastBuzz())
        assertEquals("the moment's buzz waits for its card", emptyList<HapticFeedbackType>(), momentBuzzes)

        // The view's shadow keeps only the last buzz, so a marker shows whether the success buzz comes again.
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        runTo(MOMENT_BUZZ_MS + 32)
        assertEquals("the moment adds its own buzz", listOf(HapticFeedbackType.LongPress), momentBuzzes)

        runTo(2_100)
        assertEquals("no second success buzz, at the second burst or the end", HapticFeedbackConstants.KEYBOARD_TAP, lastBuzz())
        assertEquals("the moment buzzes once", listOf(HapticFeedbackType.LongPress), momentBuzzes)
        assertEquals(1, dones)
    }

    @Test
    fun `reduced motion buzzes at once`() {
        setAnimatorScale(0f)
        setOverlay()
        assertEquals(HapticFeedbackConstants.CONFIRM, lastBuzz())
    }

    @Test
    fun `without a moment it ends after one second, not before`() {
        setOverlay()
        composeRule.mainClock.advanceTimeBy(900)
        assertEquals("still playing", 0, dones)

        composeRule.mainClock.advanceTimeBy(200)
        assertEquals(1, dones)

        composeRule.mainClock.advanceTimeBy(5_000)
        assertEquals("one celebration, one end", 1, dones)
    }

    @Test
    fun `with a moment it runs two seconds`() {
        moment = true
        setOverlay()
        composeRule.mainClock.advanceTimeBy(1_900)
        assertEquals("a moment outlasts the normal length", 0, dones)

        composeRule.mainClock.advanceTimeBy(200)
        assertEquals(1, dones)
    }

    @Test
    fun `a moment signalled during the first second still gets the full two`() {
        setOverlay()
        composeRule.mainClock.advanceTimeBy(500)
        moment = true
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeBy(1_400)
        assertEquals(0, dones)

        composeRule.mainClock.advanceTimeBy(200)
        assertEquals(1, dones)
    }

    @Test
    fun `one tap ends it at once, and the timer does not end it again`() {
        setOverlay()
        composeRule.mainClock.advanceTimeBy(200)
        composeRule.onNodeWithTag("solve.celebration").performClick()
        composeRule.mainClock.advanceTimeBy(16)
        assertEquals(1, dones)

        composeRule.mainClock.advanceTimeBy(5_000)
        assertEquals("tap and timer end it once between them", 1, dones)
    }

    @Test
    fun `reduced motion shows the stamp and keeps the same length and tap`() {
        setAnimatorScale(0f)
        setOverlay()
        composeRule.mainClock.advanceTimeBy(16)
        composeRule.onNodeWithTag("solve.celebration_stamp", useUnmergedTree = true).assertExists()
        composeRule.mainClock.advanceTimeBy(900)
        assertEquals(0, dones)
        composeRule.mainClock.advanceTimeBy(200)
        assertEquals(1, dones)
    }

    private companion object {
        /** The stamp lands this long after the overlay's first frame: once Result has slid in. */
        const val LAND_MS = 280L

        /** The stamp has settled and the first burst is over, with the normal length not yet up. */
        const val REST_MS = 960L
        const val SECOND_BURST_MS = 1_000L

        /** When a moment there on the first draw buzzes: as the moment card lands. */
        const val MOMENT_BUZZ_MS = 640L

        /** What the view's shadow reports before any buzz. */
        const val NO_BUZZ = -1
    }
}
