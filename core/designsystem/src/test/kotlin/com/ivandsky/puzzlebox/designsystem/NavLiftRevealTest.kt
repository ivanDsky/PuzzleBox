package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A recap story shrinking back by the lift (ticket 1823): into its tile when the tile is on the page below, and as a
 * short fade when it is not (scrolled off, or the list changed while the story was open). The story is pure red, the
 * page below pure blue, the tile green; the move holds both pages opaque and keeps the story on top, as the shell's lift does.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class NavLiftRevealTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var storyShown by mutableStateOf(true)
    private lateinit var lift: NavLift

    @Test
    fun `a story whose tile is gone fades out quickly instead of cutting`() {
        show(withTile = false)
        pop()
        composeRule.mainClock.advanceTimeBy(MotionTokens.ReducedFadeDuration / 2L)

        val midway = sample { w, h -> w / 2 to h / 2 }
        assertTrue("halfway the story is see-through over the page: $midway", midway.red in 0.1f..0.9f && midway.blue in 0.1f..0.9f)

        composeRule.mainClock.advanceTimeBy(MotionTokens.ReducedFadeDuration.toLong())
        val gone = sample { w, h -> w / 2 to h / 2 }
        assertTrue("the page shows, the story gone: $gone", gone.blue > 0.9f && gone.red < 0.1f)
    }

    @Test
    fun `a story shrinks into its tile, opaque, and the page shows around it`() {
        show(withTile = true)
        pop()
        composeRule.mainClock.advanceTimeBy(MotionTokens.LiftExit / 3L)

        val beside = sample { w, h -> w / 2 + besideTile to h / 2 }
        val corner = sample { w, h -> w / 20 to h / 20 }
        assertTrue("the shrinking story is still opaque beside the tile: $beside", beside.red > 0.9f && beside.blue < 0.1f)
        assertTrue("the page shows outside the shrinking story: $corner", corner.blue > 0.9f && corner.red < 0.1f)
    }

    private fun show(withTile: Boolean) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Box(Modifier.size(SIZE).background(Color.Black).testTag(ROOT)) {
                SharedTransitionLayout {
                    lift = remember { NavLift(this) }
                    CompositionLocalProvider(LocalNavLift provides lift) {
                        AnimatedContent(storyShown, transitionSpec = { liftBack(story = targetState) }, label = "probe") { story ->
                            CompositionLocalProvider(LocalNavLiftVisibility provides this) {
                                if (story) {
                                    Box(Modifier.fillMaxSize().navLiftReveal(KEY, RoundedCornerShape(28.dp)).background(Color.Red))
                                } else {
                                    Box(Modifier.fillMaxSize().background(Color.Blue)) {
                                        if (withTile) {
                                            Box(Modifier.align(Alignment.Center).size(TILE).navLiftSource(KEY, scaleContent = false).background(Color.Green))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
    }

    // What the shell's pop spec does for the screen that lifted in: picked as the move starts, before the page below
    // composes, with both pages held opaque and the popped one kept on top.
    private fun liftBack(story: Boolean): ContentTransform {
        lift.key = KEY
        lift.lifting = !story
        lift.liftMillis = MotionTokens.LiftExit
        return ContentTransform(
            targetContentEnter = fadeIn(tween(MotionTokens.LiftExit), initialAlpha = 1f),
            initialContentExit = fadeOut(tween(MotionTokens.LiftExit), targetAlpha = 1f),
            targetContentZIndex = if (story) 0f else -1f,
        )
    }

    // With the clock paused nothing idles the main looper, so the deferred snapshot apply never runs; apply it here.
    private fun pop() {
        composeRule.runOnIdle {
            storyShown = false
            Snapshot.sendApplyNotifications()
        }
    }

    private fun sample(at: (Int, Int) -> Pair<Int, Int>): Color {
        val pixels = composeRule.onNodeWithTag(ROOT).captureToImage().toPixelMap()
        val (x, y) = at(pixels.width, pixels.height)
        return pixels[x, y]
    }

    // Past the tile's own edge (half of TILE), well inside the shrinking story a third of the way back.
    private val besideTile: Int get() = with(composeRule.density) { 32.dp.roundToPx() }

    private companion object {
        val SIZE = 300.dp
        val TILE = 40.dp
        const val KEY = "recap"
        const val ROOT = "probe.root"
        const val SETTLE_MILLIS = 2_000L
    }
}
