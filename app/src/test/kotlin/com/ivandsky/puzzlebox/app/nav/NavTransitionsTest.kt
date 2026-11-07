package com.ivandsky.puzzlebox.app.nav

import android.content.ContentResolver
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Polish 04 navigation motion. First the chooser's pins per case (phone B / tablet D × open,
 * pop, result, tab, reduced): durations, easing, parallax, scrim and which page is on top.
 * Then each transform on screen: a real NavDisplay (so its push/pop stacking applies) on a
 * paused clock, reading where the pages are and how they composite mid-flight. Pages are
 * pure blue and red over black, so a sampled pixel whose red + blue is short of full means
 * the backdrop shows through: two pages fading at once.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class NavTransitionsTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ---- pins: phone (B) ----

    @Test
    fun `phone open slides the game in on top over a parallaxed, scrimmed page`() {
        val motion = navMotion(playRoot, game, pop = false, reduceMotion = false, NavForm.Phone)

        assertEquals(
            NavMotion(
                NavMotionKind.Slide,
                pop = false,
                durationMillis = MotionTokens.NavFullSlideEnter,
                easing = MotionTokens.EasingEmphasizedDecelerate,
                parallaxFraction = MotionTokens.NavParallaxFraction,
                scrimAlpha = MotionTokens.NavScrimAlpha,
            ),
            motion,
        )
        assertEquals(380, motion.durationMillis)
        assertEquals(0.28f, motion.parallaxFraction)
        assertEquals(0.32f, motion.scrimAlpha)
        assertTrue(motion.incomingOnTop)
    }

    @Test
    fun `phone pop mirrors the slide on the shorter standard clock, with the leaving page on top`() {
        val motion = navMotion(game, playRoot, pop = true, reduceMotion = false, NavForm.Phone)

        assertEquals(NavMotionKind.Slide, motion.kind)
        assertEquals(280, motion.durationMillis)
        assertEquals(MotionTokens.EasingStandard, motion.easing)
        assertEquals(MotionTokens.NavParallaxFraction, motion.parallaxFraction)
        assertEquals(MotionTokens.NavScrimAlpha, motion.scrimAlpha)
        assertTrue(!motion.incomingOnTop)
    }

    @Test
    fun `phone result is the same slide both ways`() {
        val open = navMotion(playRoot, game, pop = false, reduceMotion = false, NavForm.Phone)
        val back = navMotion(game, playRoot, pop = true, reduceMotion = false, NavForm.Phone)

        assertEquals(open, navMotion(game, result, pop = false, reduceMotion = false, NavForm.Phone))
        assertEquals(open, navMotion(result, game, pop = false, reduceMotion = false, NavForm.Phone))
        assertEquals(back, navMotion(result, playRoot, pop = true, reduceMotion = false, NavForm.Phone))
    }

    @Test
    fun `phone tab switch is a fade-through on the tab clock`() {
        val motion = navMotion(homeRoot, playRoot, pop = false, reduceMotion = false, NavForm.Phone)

        assertEquals(NavMotionKind.FadeThrough, motion.kind)
        assertEquals(120, motion.exitMillis)
        assertEquals(240, motion.durationMillis)
        assertEquals(100, motion.enterDelayMillis)
        assertEquals(0f, motion.scrimAlpha)
        assertEquals(NavMotionKind.FadeThrough, navMotion(playRoot, homeRoot, pop = true, reduceMotion = false, NavForm.Phone).kind)
    }

    // ---- pins: tablet (D) ----

    @Test
    fun `tablet open lifts the game out of its card under a light scrim`() {
        val motion = navMotion(playRoot, game, pop = false, reduceMotion = false, NavForm.Tablet, liftable = true)

        assertEquals(NavMotionKind.Lift, motion.kind)
        assertEquals(MotionTokens.LiftEnter, motion.durationMillis)
        assertEquals(400, motion.durationMillis)
        assertEquals(MotionTokens.EasingStandard, motion.easing)
        assertEquals(0f, motion.parallaxFraction)
        assertEquals(0.2f, motion.scrimAlpha)
        assertTrue(motion.incomingOnTop)
    }

    @Test
    fun `tablet pop shrinks a lifted game back into its card`() {
        val motion = navMotion(game, playRoot, pop = true, reduceMotion = false, NavForm.Tablet, liftable = true)

        assertEquals(NavMotionKind.Lift, motion.kind)
        assertEquals(300, motion.durationMillis)
        assertEquals(MotionTokens.LiftScrimAlpha, motion.scrimAlpha)
        assertTrue(!motion.incomingOnTop)
    }

    @Test
    fun `tablet open without a card on screen falls back to the phone slide`() {
        assertEquals(
            navMotion(playRoot, game, pop = false, reduceMotion = false, NavForm.Phone),
            navMotion(playRoot, game, pop = false, reduceMotion = false, NavForm.Tablet, liftable = false),
        )
        assertEquals(
            navMotion(game, playRoot, pop = true, reduceMotion = false, NavForm.Phone),
            navMotion(game, playRoot, pop = true, reduceMotion = false, NavForm.Tablet, liftable = false),
        )
    }

    @Test
    fun `tablet game to result and back travel the full width together, without a scrim`() {
        val push = navMotion(game, result, pop = false, reduceMotion = false, NavForm.Tablet, liftable = true)
        val again = navMotion(result, game, pop = false, reduceMotion = false, NavForm.Tablet, liftable = true)
        val done = navMotion(result, playRoot, pop = true, reduceMotion = false, NavForm.Tablet)

        assertEquals(NavMotion(NavMotionKind.Axis, pop = false, durationMillis = 360, easing = MotionTokens.EasingStandard), push)
        assertEquals(push, again)
        assertEquals(NavMotion(NavMotionKind.Axis, pop = true, durationMillis = 300, easing = MotionTokens.EasingStandard), done)
    }

    @Test
    fun `tablet tab switch rises over the opaque outgoing tab`() {
        val motion = navMotion(homeRoot, playRoot, pop = false, reduceMotion = false, NavForm.Tablet)

        assertEquals(NavMotion(NavMotionKind.Rise, pop = false, durationMillis = 220, easing = MotionTokens.EasingStandard), motion)
        assertEquals(0f, motion.scrimAlpha)
        assertEquals(NavMotionKind.Rise, navMotion(playRoot, homeRoot, pop = true, reduceMotion = false, NavForm.Tablet).kind)
    }

    // ---- pins: reduced and predictive ----

    @Test
    fun `reduced motion is one short fade for every case and form`() {
        val cases = NavForm.entries.flatMap { form ->
            listOf(
                navMotion(playRoot, game, pop = false, reduceMotion = true, form, liftable = true),
                navMotion(game, playRoot, pop = true, reduceMotion = true, form, liftable = true),
                navMotion(game, result, pop = false, reduceMotion = true, form),
                navMotion(homeRoot, playRoot, pop = false, reduceMotion = true, form),
                navMotion(playRoot, homeRoot, pop = true, reduceMotion = true, form),
            )
        }

        cases.forEach { motion ->
            assertEquals(NavMotionKind.Reduced, motion.kind)
            assertEquals(MotionTokens.ReducedFadeDuration, motion.durationMillis)
            assertEquals(0f, motion.scrimAlpha)
            assertEquals(0f, motion.parallaxFraction)
        }
    }

    @Test
    fun `predictive back keeps its scrub, never scrimmed`() {
        val motion = predictivePopMotion(NavigationEvent.EDGE_LEFT, reduceMotion = false)

        assertEquals(NavMotionKind.Predictive, motion.kind)
        assertEquals(NavMotion.PredictiveMillis, motion.durationMillis)
        assertEquals(0f, motion.scrimAlpha)
        assertTrue(!motion.incomingOnTop)
        assertEquals(NavMotionKind.Reduced, predictivePopMotion(NavigationEvent.EDGE_LEFT, reduceMotion = true).kind)
    }

    // ---- on screen ----

    @Test
    fun `slide push brings the game from the end edge over a page parallaxing toward the start`() {
        val motion = navMotion(playRoot, game, pop = false, reduceMotion = false, NavForm.Phone)
        val frame = Probe(composeRule).play(motion)

        assertTrue("incoming starts toward the end: $frame", frame.incoming.left > frame.container.left + EPS)
        val shift = frame.container.left - frame.outgoing.left
        assertTrue("outgoing parallaxes left: $frame", shift > EPS)
        assertTrue("by at most the parallax: $frame", shift <= WIDTH.value * MotionTokens.NavParallaxFraction + EPS)
        assertTrue("the outgoing lags the incoming: $frame", shift < frame.incoming.left - frame.container.left)
    }

    @Test
    fun `slide pop pulls the game off toward the end over the returning page`() {
        val motion = navMotion(game, playRoot, pop = true, reduceMotion = false, NavForm.Phone)
        val frame = Probe(composeRule).play(motion)

        assertTrue("outgoing leaves toward the end: $frame", frame.outgoing.left > frame.container.left + EPS)
        assertTrue("incoming returns from the start: $frame", frame.incoming.left < frame.container.left - EPS)
        assertOpaque(frame)
    }

    @Test
    fun `right-to-left slide push enters from the left`() {
        val motion = navMotion(playRoot, game, pop = false, reduceMotion = false, NavForm.Phone)
        val frame = Probe(composeRule, LayoutDirection.Rtl).play(motion)

        assertTrue("incoming starts on the left: $frame", frame.incoming.right < frame.container.right - EPS)
        assertTrue("outgoing parallaxes right: $frame", frame.outgoing.left > frame.container.left + EPS)
    }

    @Test
    fun `axis moves both pages together at full width, opaque`() {
        val probe = Probe(composeRule)
        for (pop in listOf(false, true)) {
            val motion = if (pop) {
                navMotion(result, playRoot, pop = true, reduceMotion = false, NavForm.Tablet)
            } else {
                navMotion(game, result, pop = false, reduceMotion = false, NavForm.Tablet)
            }
            val frame = probe.play(motion)

            val gap = abs(frame.incoming.left - frame.outgoing.left)
            assertEquals("pages abut: $frame", WIDTH.value, gap, EPS)
            assertTrue("in flight: $frame", abs(frame.incoming.left - frame.container.left) > EPS)
        }
    }

    @Test
    fun `lift holds both scenes still and opaque, so only the shared card moves`() {
        val probe = Probe(composeRule)
        for (pop in listOf(false, true)) {
            val motion = navMotion(if (pop) game else playRoot, if (pop) playRoot else game, pop, false, NavForm.Tablet, liftable = true)
            val frame = probe.play(motion)

            assertStill(frame)
            assertTrue("the upper page is fully drawn: $frame", frame.upper(pop) >= 254)
        }
    }

    @Test
    fun `tablet tab switch fades the incoming over an outgoing tab that stays at alpha 1`() {
        val motion = navMotion(homeRoot, playRoot, pop = false, reduceMotion = false, NavForm.Tablet)
        val frame = Probe(composeRule).play(motion)

        assertOpaque(frame)
        assertTrue("incoming is still fading in: $frame", frame.incomingChannel in 1..254)
        assertTrue("incoming rises: $frame", frame.incoming.top > frame.container.top + EPS)
        assertTrue("outgoing holds still: $frame", abs(frame.outgoing.top - frame.container.top) < EPS)
    }

    @Test
    fun `tablet tab back to Home fades only the upper page`() {
        val motion = navMotion(playRoot, homeRoot, pop = true, reduceMotion = false, NavForm.Tablet)
        val frame = Probe(composeRule).play(motion)

        assertOpaque(frame)
        assertTrue("outgoing is fading off the destination: $frame", frame.outgoingChannel in 1..254)
    }

    @Test
    fun `phone tab switch fades only, with no travel`() {
        assertStill(Probe(composeRule).play(navTransition(homeRoot, playRoot, pop = false, NavForm.Phone)))
    }

    @Test
    fun `reduced motion fades the upper page in place for every case`() {
        val probe = Probe(composeRule)
        val cases = NavForm.entries.flatMap { form ->
            listOf(
                navMotion(playRoot, game, pop = false, reduceMotion = true, form, liftable = true),
                navMotion(game, playRoot, pop = true, reduceMotion = true, form),
                navMotion(homeRoot, playRoot, pop = false, reduceMotion = true, form),
                navMotion(playRoot, homeRoot, pop = true, reduceMotion = true, form),
            )
        } + predictivePopMotion(NavigationEvent.EDGE_LEFT, reduceMotion = true)

        cases.forEach { motion ->
            val frame = probe.play(motion)
            assertStill(frame)
            assertOpaque(frame)
            assertTrue("upper page is mid-fade: $frame", frame.upper(motion.pop) in 1..254)
        }
    }

    @Test
    fun `predictive back shifts the screen toward the swipe over an opaque, still destination`() {
        val probe = Probe(composeRule)

        val fromLeft = probe.play(predictivePopMotion(NavigationEvent.EDGE_LEFT, reduceMotion = false), PREDICTIVE_HALF)
        assertTrue("left-edge swipe pulls right: $fromLeft", fromLeft.outgoing.left > fromLeft.container.left + EPS)
        assertSameRect(fromLeft.container, fromLeft.incoming)

        val fromRight = probe.play(predictivePopMotion(NavigationEvent.EDGE_RIGHT, reduceMotion = false), PREDICTIVE_HALF)
        assertTrue("right-edge swipe pulls left: $fromRight", fromRight.outgoing.left < fromRight.container.left - EPS)
        assertSameRect(fromRight.container, fromRight.incoming)
        assertOpaque(fromRight)
    }

    @Test
    fun `the reduce-motion flag flips live when the animator scale changes`() {
        lateinit var resolver: ContentResolver
        var reduced: Boolean? = null
        composeRule.setContent {
            resolver = LocalContext.current.contentResolver
            reduced = rememberReducedMotion()
        }
        composeRule.runOnIdle { assertEquals(false, reduced) }

        setAnimatorScale(resolver, 0f)
        composeRule.runOnIdle { assertEquals(true, reduced) }

        setAnimatorScale(resolver, 1f)
        composeRule.runOnIdle { assertEquals(false, reduced) }
    }

    private fun navTransition(from: Map<String, Any>, to: Map<String, Any>, pop: Boolean, form: NavForm) =
        navMotion(from, to, pop, reduceMotion = false, form)

    private fun setAnimatorScale(resolver: ContentResolver, scale: Float) {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
        resolver.notifyChange(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), null)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun assertStill(frame: Frame) {
        assertSameRect(frame.container, frame.incoming)
        assertSameRect(frame.container, frame.outgoing)
    }

    /** The sampled pixel is fully covered: never two pages fading at once. */
    private fun assertOpaque(frame: Frame) {
        assertTrue("backdrop shows through: $frame", abs(255 - frame.incomingChannel - frame.outgoingChannel) <= 2)
    }

    private fun assertSameRect(expected: Rect, actual: Rect) {
        val close = abs(expected.left - actual.left) < EPS && abs(expected.top - actual.top) < EPS &&
            abs(expected.right - actual.right) < EPS && abs(expected.bottom - actual.bottom) < EPS
        assertTrue("expected $actual to stay at $expected (no travel)", close)
    }

    private data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float)

    /** Page bounds plus the colour channel each page contributes at the sample point. */
    private data class Frame(
        val container: Rect,
        val incoming: Rect,
        val outgoing: Rect,
        val incomingChannel: Int,
        val outgoingChannel: Int,
    ) {
        fun upper(pop: Boolean): Int = if (pop) outgoingChannel else incomingChannel
    }

    /**
     * NavDisplay over a two-key stack whose every move plays the transform it is handed: a push
     * goes [A] → [A, B], a pop [A, B] → [A]. A is pure blue, B pure red, over black.
     */
    private class Probe(
        private val rule: ComposeContentTestRule,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) {
        private val stack = mutableStateListOf<NavKey>(PAGE_A)
        private var next: ContentTransform = INSTANT
        private val motion = mutableStateOf(NavMotion.Idle)
        private lateinit var density: Density
        private val direction = layoutDirection

        init {
            rule.mainClock.autoAdvance = false
            rule.setContent {
                density = LocalDensity.current
                // The shell's own scene canvas wraps each page, so its clock keeps a held page
                // composed exactly as in the app; a clear scrim keeps the pixel reading honest.
                MaterialTheme(colorScheme = lightColorScheme(scrim = Color.Transparent, background = Color.Black)) {
                    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection, LocalNavMotion provides motion) {
                        Box(Modifier.size(WIDTH, HEIGHT).background(Color.Black).testTag(CONTAINER)) {
                            NavDisplay(
                                backStack = stack,
                                onBack = {},
                                transitionSpec = { next },
                                popTransitionSpec = { next },
                                predictivePopTransitionSpec = { next },
                                entryProvider = { key ->
                                    NavEntry(key) {
                                        val color = if (key == PAGE_A) Color.Blue else Color.Red
                                        SceneCanvas(entryKey = null) {
                                            Box(Modifier.fillMaxSize().background(color).testTag(tagOf(key)))
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        /** Plays [motion] from its resting stack and samples [atMillis] in, then settles. */
        fun play(motion: NavMotion, atMillis: Long = SAMPLE_MILLIS): Frame {
            move(INSTANT, NavMotion.Idle) { if (motion.pop) ensureTop(PAGE_B) else ensureTop(PAGE_A) }
            rule.mainClock.advanceTimeBy(SETTLE_MILLIS)
            val transform = motion.transform(direction, density)
            move(transform, motion) { if (motion.pop) stack.removeAt(stack.lastIndex) else stack.add(PAGE_B) }
            rule.mainClock.advanceTimeBy(atMillis)
            val incoming = if (motion.pop) PAGE_A else PAGE_B
            val outgoing = if (motion.pop) PAGE_B else PAGE_A
            val pixel = rule.onNodeWithTag(CONTAINER).captureToImage().toPixelMap().let { it[it.width / 2, it.height / 2] }
            val red = (pixel.red * 255).toInt()
            val blue = (pixel.blue * 255).toInt()
            val frame = Frame(
                container = bounds(CONTAINER),
                incoming = bounds(tagOf(incoming)),
                outgoing = bounds(tagOf(outgoing)),
                incomingChannel = if (incoming == PAGE_B) red else blue,
                outgoingChannel = if (outgoing == PAGE_B) red else blue,
            )
            rule.mainClock.advanceTimeBy(SETTLE_MILLIS)
            return frame
        }

        private fun ensureTop(key: NavKey) {
            if (key == PAGE_A && stack.size > 1) stack.removeAt(stack.lastIndex)
            if (key == PAGE_B && stack.size == 1) stack.add(PAGE_B)
        }

        // With the clock paused nothing idles the main looper, so the platform's deferred
        // snapshot apply never runs; apply the write ourselves or the move is never seen.
        private fun move(transform: ContentTransform, playing: NavMotion, change: () -> Unit) {
            rule.runOnIdle {
                next = transform
                motion.value = playing
                change()
                Snapshot.sendApplyNotifications()
            }
        }

        private fun bounds(tag: String): Rect = rule.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot().let {
            Rect(it.left.value, it.top.value, it.right.value, it.bottom.value)
        }

        private fun tagOf(key: NavKey) = if (key == PAGE_A) "probe.a" else "probe.b"
    }

    private data object ProbeA : NavKey
    private data object ProbeB : NavKey

    private companion object {
        val INSTANT: ContentTransform = EnterTransition.None togetherWith ExitTransition.None

        /** Fits Robolectric's default 320 dp-wide window, so the container itself is never clipped. */
        val WIDTH = 300.dp
        val HEIGHT = 400.dp
        const val CONTAINER = "probe.container"
        val PAGE_A: NavKey = ProbeA
        val PAGE_B: NavKey = ProbeB

        /** Well into every curve, and before the shortest move (the reduced fade) ends. */
        const val SAMPLE_MILLIS = 50L
        val PREDICTIVE_HALF = NavMotion.PredictiveMillis / 2L
        const val SETTLE_MILLIS = 3_000L
        const val EPS = 0.5f

        fun entry(owner: NavKey, key: NavKey): Map<String, Any> = mapOf(OWNER_TAB to owner, ENTRY_KEY to key)

        val homeRoot = entry(HomeKey, HomeKey)
        val playRoot = entry(PlayKey, PlayKey)
        val game = entry(PlayKey, GameKey("zip"))
        val result = entry(PlayKey, ResultKey(gameId = "zip", day = "2026-03-09", timeMs = 61_000, hintsUsed = 0))
    }
}
