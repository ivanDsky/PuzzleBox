package com.ivandsky.puzzlebox.train

import androidx.compose.runtime.Composable
import com.ivandsky.puzzlebox.gameui.trainer.IntroVariant
import com.ivandsky.puzzlebox.train.practice.IntroEntry
import com.ivandsky.puzzlebox.train.practice.StandInGrid
import com.ivandsky.puzzlebox.train.practice.StandInIntroDemo
import com.ivandsky.puzzlebox.train.practice.WatchPane
import com.ivandsky.puzzlebox.train.practice.standInChapter
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class TrainPhoneWatchGoldenScreenshots : TrainGoldenScreenshots("phone") {
    @Test fun `watch with one variant light`() = capture("watch", dark = false) { GoldenPage { Watch(count = 1, showBack = true, frame = LastFrame) } }
    @Test fun `watch with two variants light`() = capture("watch-variants", dark = false) { GoldenPage { Watch(count = 2, showBack = true) } }
    @Test fun `watch with two variants dark`() = capture("watch-variants", dark = true) { GoldenPage { Watch(count = 2, showBack = true) } }
    @Test fun `watch replay light`() = capture("watch-replay", dark = false) { GoldenPage { Watch(count = 1, showBack = true, replay = true, frame = LastFrame) } }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class TrainTabletWatchGoldenScreenshots : TrainGoldenScreenshots("tablet") {
    @Test fun `watch with one variant light`() = capture("watch", dark = false) { GoldenBeside(open = true) { Watch(count = 1, showBack = false, frame = LastFrame) } }
    @Test fun `watch with two variants light`() = capture("watch-variants", dark = false) { GoldenBeside(open = true) { Watch(count = 2, showBack = false) } }
    @Test fun `watch replay light`() = capture("watch-replay", dark = false) { GoldenBeside(open = true) { Watch(count = 1, showBack = false, replay = true, frame = LastFrame) } }
}

/** The stand-in demo's last frame: the squares landed, so the main button is Start practice. */
private const val LastFrame = 3

/** The intro's watching screen, [count] variants, variant 0 on [frame]: 1 is the evidence (with Next step), [LastFrame] its last step (Start practice). */
@Composable
private fun Watch(count: Int, showBack: Boolean, replay: Boolean = false, frame: Int = 1) = WatchPane(
    chapter = standInChapter(board = 1, boards = 5),
    progress = GoldenProgressTwo,
    showBack = showBack,
    onClose = {},
    entries = (0 until count).map {
        IntroEntry(
            IntroVariant("Variant $it", "A color in one line leaves the rest of the line.", "Find a color all in one row.", "Cross out the rest of the row."),
            StandInIntroDemo(it, listOf("Here is the position.", "The evidence lights up.", "The first square lands.", "The line is done.")),
        )
    },
    variant = 0,
    frame = frame,
    replay = replay,
    demo = { _, _, modifier -> StandInGrid(modifier) },
    onSelect = {},
    onSeek = {},
    onReplay = {},
    onNext = {},
    onLeave = {},
    onPrimary = {},
)
