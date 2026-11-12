package com.ivandsky.puzzlebox.play

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.gameui.replay.ReplayPage
import com.ivandsky.puzzlebox.gameui.replay.ReplayTags
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The replay page (top bar and single-board player) over the replay fixtures, phone and tablet, light and dark, Skip idle on: a short
 * (12 moves) and a long (64 moves over about an hour) replay opened paused on the finished board, playing a few seconds in, and
 * (short) paused mid-replay. Playing is reached through the real Play button and the frozen clock.
 * Goldens in `snapshots/replay-player/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class ReplayPlayerGoldenScreenshotsPhone : ReplayPlayerGoldenScreenshots("phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class ReplayPlayerGoldenScreenshotsTablet : ReplayPlayerGoldenScreenshots("tablet")

abstract class ReplayPlayerGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `short end light`() = capture("short-end", SHORT, dark = false)
    @Test fun `short end dark`() = capture("short-end", SHORT, dark = true)

    @Test fun `short playing light`() = capture("short-playing", SHORT, dark = false, playMs = PLAY_MS)
    @Test fun `short playing dark`() = capture("short-playing", SHORT, dark = true, playMs = PLAY_MS)

    @Test fun `short paused light`() = capture("short-paused", SHORT, dark = false, playMs = PLAY_MS, pause = true)
    @Test fun `short paused dark`() = capture("short-paused", SHORT, dark = true, playMs = PLAY_MS, pause = true)

    @Test fun `long end light`() = capture("long-end", LONG, dark = false)
    @Test fun `long end dark`() = capture("long-end", LONG, dark = true)

    @Test fun `long playing light`() = capture("long-playing", LONG, dark = false, playMs = PLAY_MS)
    @Test fun `long playing dark`() = capture("long-playing", LONG, dark = true, playMs = PLAY_MS)

    private fun capture(name: String, timeline: Timeline, dark: Boolean, playMs: Long = 0, pause: Boolean = false) {
        rule.showGolden(dark) { ReplayPage(timeline, ReplayGoldenBoard, context = "Fixture · #1 · Today", onBack = {}) }
        if (playMs > 0) {
            rule.onNodeWithTag(ReplayTags.PLAY).performClick()
            rule.mainClock.advanceTimeBy(playMs)
            if (pause) rule.onNodeWithTag(ReplayTags.PLAY).performClick()
        }
        rule.settleAndCapture("replay-player", name, device, dark)
    }

    private companion object {
        const val PLAY_MS = 5_000L
        const val LONG_MOVES = 64
        const val LONG_STEP_MS = 64_000L
        val SHORT = ReplayFixtures.timeline(ReplayFixtures.SHORT_MOVES)
        val LONG = ReplayFixtures.timeline(LONG_MOVES, stepMs = LONG_STEP_MS)
    }
}
