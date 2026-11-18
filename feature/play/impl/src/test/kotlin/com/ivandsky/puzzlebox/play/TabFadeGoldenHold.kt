package com.ivandsky.puzzlebox.play

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.io.File

/**
 * Holds a real finger [fraction] of the pager's width along its way to the next tab, without lifting it, and captures
 * the screen to `snapshots/tab-fade/<name>-<device>-<light|dark>.png`: the frame a swipe is mid-way through. The
 * entrances are settled first, and the drag adds the touch slop the pager takes before it follows the finger, so the
 * pager is [fraction] along, not a slop short of it.
 */
@OptIn(ExperimentalRoborazziApi::class)
internal fun ComposeContentTestRule.holdSwipeAndCapture(fraction: Float, name: String, device: String, dark: Boolean) {
    mainClock.autoAdvance = false
    mainClock.advanceTimeBy(SETTLE_MS)
    onNodeWithTag("underline.pager").performTouchInput {
        down(center)
        moveBy(Offset(-(width * fraction + viewConfiguration.touchSlop), 0f), delayMillis = HOLD_MS)
    }
    // What composes from the pager's position (the tablet's right pane) follows it a frame behind: let it catch up.
    repeat(CATCH_UP_FRAMES) { mainClock.advanceTimeByFrame() }
    val file = File(checkNotNull(System.getProperty("user.dir")))
        .resolve("snapshots/tab-fade/$name-$device-${if (dark) "dark" else "light"}.png")
        .canonicalFile
        .also { checkNotNull(it.parentFile).mkdirs() }
    captureScreenRoboImage(file.absolutePath)
}

private const val SETTLE_MS = 2_000L
private const val HOLD_MS = 400L
private const val CATCH_UP_FRAMES = 3
