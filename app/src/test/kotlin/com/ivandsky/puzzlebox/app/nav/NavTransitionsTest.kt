package com.ivandsky.puzzlebox.app.nav

import android.content.ContentResolver
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigationevent.NavigationEvent
import com.ivandsky.puzzlebox.gameui.rememberReducedMotion
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
 * A19/A20 motion, observed on screen: each chooser result drives a real AnimatedContent
 * on a paused clock, and the test reads where the incoming and outgoing screens are
 * mid-flight. Travel shows as an offset, the Result scale as centred shrinking, and a
 * fade as neither (only opacity moves). The reduce-motion flag is read live.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class NavTransitionsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `same owner push slides the new screen in from the end edge`() {
        val frame = Probe(composeRule).play(navTransition(playRoot, game, pop = false, reduceMotion = false, LTR))

        assertTrue("incoming starts toward the end: $frame", frame.incoming.left.value > frame.container.left.value + EPS)
        assertTrue("outgoing leaves toward the start: $frame", frame.outgoing.right.value < frame.container.right.value - EPS)
        assertUnscaled(frame.container, frame.incoming)
    }

    @Test
    fun `same owner pop slides the previous screen in from the start edge`() {
        val frame = Probe(composeRule).play(navTransition(game, playRoot, pop = true, reduceMotion = false, LTR))

        assertTrue("incoming starts toward the start: $frame", frame.incoming.right.value < frame.container.right.value - EPS)
        assertTrue("outgoing leaves toward the end: $frame", frame.outgoing.left.value > frame.container.left.value + EPS)
        assertUnscaled(frame.container, frame.incoming)
    }

    @Test
    fun `right-to-left push enters from the end edge, which is the left`() {
        val frame = Probe(composeRule, LayoutDirection.Rtl)
            .play(navTransition(playRoot, game, pop = false, reduceMotion = false, LayoutDirection.Rtl))

        assertTrue("incoming starts on the left: $frame", frame.incoming.right.value < frame.container.right.value - EPS)
        assertTrue("outgoing leaves to the right: $frame", frame.outgoing.left.value > frame.container.left.value + EPS)
    }

    @Test
    fun `a different owner tab fades only, in both directions`() {
        val probe = Probe(composeRule)

        assertStill(probe.play(navTransition(homeRoot, playRoot, pop = false, reduceMotion = false, LTR)))
        assertStill(probe.play(navTransition(playRoot, homeRoot, pop = true, reduceMotion = false, LTR)))
    }

    @Test
    fun `result push scales in`() {
        val frame = Probe(composeRule).play(navTransition(game, result, pop = false, reduceMotion = false, LTR))

        assertShrunkInPlace(frame.container, frame.incoming)
        assertSameRect(frame.container, frame.outgoing)
    }

    @Test
    fun `result pop exits along its entry path`() {
        val frame = Probe(composeRule).play(navTransition(result, playRoot, pop = true, reduceMotion = false, LTR))

        assertShrunkInPlace(frame.container, frame.outgoing)
        assertSameRect(frame.container, frame.incoming)
    }

    @Test
    fun `result under reduced motion fades only`() {
        val probe = Probe(composeRule)

        assertStill(probe.play(navTransition(game, result, pop = false, reduceMotion = true, LTR)))
        assertStill(probe.play(navTransition(result, playRoot, pop = true, reduceMotion = true, LTR)))
    }

    @Test
    fun `reduced motion fades only for every case`() {
        val probe = Probe(composeRule)
        val cases = listOf(
            navTransition(playRoot, game, pop = false, reduceMotion = true, LTR),
            navTransition(game, playRoot, pop = true, reduceMotion = true, LTR),
            navTransition(playRoot, game, pop = false, reduceMotion = true, LayoutDirection.Rtl),
            navTransition(homeRoot, playRoot, pop = false, reduceMotion = true, LTR),
            navTransition(playRoot, homeRoot, pop = true, reduceMotion = true, LTR),
            navTransition(game, result, pop = false, reduceMotion = true, LTR),
            navTransition(result, playRoot, pop = true, reduceMotion = true, LTR),
            predictivePopTransition(game, playRoot, NavigationEvent.EDGE_LEFT, reduceMotion = true),
            predictivePopTransition(game, playRoot, NavigationEvent.EDGE_RIGHT, reduceMotion = true),
        )

        cases.forEach { assertStill(probe.play(it)) }
    }

    @Test
    fun `predictive back shrinks the screen toward the swipe over a still destination`() {
        val probe = Probe(composeRule)

        val fromLeft = probe.play(predictivePopTransition(game, playRoot, NavigationEvent.EDGE_LEFT, reduceMotion = false), PREDICTIVE_HALF)
        assertShrunk(fromLeft.container, fromLeft.outgoing)
        assertTrue("left-edge swipe pulls right: $fromLeft", fromLeft.outgoing.centerX > fromLeft.container.centerX + EPS)
        assertSameRect(fromLeft.container, fromLeft.incoming)

        val fromRight = probe.play(predictivePopTransition(result, playRoot, NavigationEvent.EDGE_RIGHT, reduceMotion = false), PREDICTIVE_HALF)
        assertShrunk(fromRight.container, fromRight.outgoing)
        assertTrue("right-edge swipe pulls left: $fromRight", fromRight.outgoing.centerX < fromRight.container.centerX - EPS)
        assertSameRect(fromRight.container, fromRight.incoming)
    }

    @Test
    fun `predictive back across tabs scrubs opacity only`() {
        assertStill(
            Probe(composeRule).play(predictivePopTransition(playRoot, homeRoot, NavigationEvent.EDGE_LEFT, reduceMotion = false), PREDICTIVE_HALF),
        )
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

    private fun setAnimatorScale(resolver: ContentResolver, scale: Float) {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
        resolver.notifyChange(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), null)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun assertStill(frame: Frame) {
        assertSameRect(frame.container, frame.incoming)
        assertSameRect(frame.container, frame.outgoing)
    }

    private fun assertSameRect(expected: DpRect, actual: DpRect) {
        val close = abs(expected.left.value - actual.left.value) < EPS &&
            abs(expected.top.value - actual.top.value) < EPS &&
            abs(expected.right.value - actual.right.value) < EPS &&
            abs(expected.bottom.value - actual.bottom.value) < EPS
        assertTrue("expected $actual to stay at $expected (no travel, no scale)", close)
    }

    private fun assertUnscaled(container: DpRect, screen: DpRect) {
        assertTrue("$screen keeps full height in $container", abs(screen.top.value - container.top.value) < EPS)
        assertTrue("$screen keeps full height in $container", abs(screen.bottom.value - container.bottom.value) < EPS)
    }

    private fun assertShrunk(container: DpRect, screen: DpRect) {
        assertTrue("$screen is scaled down in $container", screen.top.value > container.top.value + EPS)
        assertTrue("$screen is scaled down in $container", screen.bottom.value < container.bottom.value - EPS)
    }

    private fun assertShrunkInPlace(container: DpRect, screen: DpRect) {
        assertShrunk(container, screen)
        assertTrue("$screen is centred in $container", abs(screen.centerX - container.centerX) < EPS)
    }

    private data class Frame(val container: DpRect, val incoming: DpRect, val outgoing: DpRect)

    /** One AnimatedContent that flips between two screens with whatever transform it is handed. */
    private class Probe(
        private val rule: ComposeContentTestRule,
        layoutDirection: LayoutDirection = LTR,
    ) {
        private var screen by mutableStateOf(SCREEN_A)
        private var next: ContentTransform = EnterTransition.None togetherWith ExitTransition.None

        init {
            rule.mainClock.autoAdvance = false
            rule.setContent {
                CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                    Box(Modifier.size(WIDTH, HEIGHT).testTag(CONTAINER)) {
                        AnimatedContent(targetState = screen, transitionSpec = { next }, label = "probe") { tag ->
                            Box(Modifier.fillMaxSize().testTag(tag))
                        }
                    }
                }
            }
        }

        /** Navigates with [transform] and returns both screens' bounds [atMillis] in, then settles. */
        fun play(transform: ContentTransform, atMillis: Long = SAMPLE_MILLIS): Frame {
            val outgoing = screen
            val incoming = if (outgoing == SCREEN_A) SCREEN_B else SCREEN_A
            // With the clock paused nothing idles the main looper, so the platform's deferred
            // snapshot apply never runs; apply the write ourselves or the flip is never seen.
            rule.runOnIdle {
                next = transform
                screen = incoming
                Snapshot.sendApplyNotifications()
            }
            rule.mainClock.advanceTimeBy(atMillis)
            val frame = Frame(bounds(CONTAINER), bounds(incoming), bounds(outgoing))
            rule.mainClock.advanceTimeBy(SETTLE_MILLIS)
            return frame
        }

        private fun bounds(tag: String): DpRect = rule.onNodeWithTag(tag).getBoundsInRoot()
    }

    private companion object {
        val LTR = LayoutDirection.Ltr
        /** Fits Robolectric's default 320 dp-wide window, so the container itself is never clipped. */
        val WIDTH = 300.dp
        val HEIGHT = 400.dp
        const val CONTAINER = "probe.container"
        const val SCREEN_A = "probe.a"
        const val SCREEN_B = "probe.b"

        /** Well into every travel/scale curve, and before any transition has settled. */
        const val SAMPLE_MILLIS = 100L
        val PREDICTIVE_HALF = NavMotion.PredictiveMillis / 2L
        const val SETTLE_MILLIS = 3_000L
        const val EPS = 0.5f

        fun entry(owner: NavKey, key: NavKey): Map<String, Any> = mapOf(OWNER_TAB to owner, ENTRY_KEY to key)

        val homeRoot = entry(HomeKey, HomeKey)
        val playRoot = entry(PlayKey, PlayKey)
        val game = entry(PlayKey, GameKey("zip"))
        val result = entry(PlayKey, ResultKey(timeMs = 61_000, hintsUsed = 0))

        val DpRect.centerX: Float get() = (left.value + right.value) / 2
    }
}
