package com.ivandsky.puzzlebox.train.practice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.trainer.IntroDemo
import com.ivandsky.puzzlebox.gameui.trainer.IntroVariant

// The technique intro: the watching screen on a phone and a tablet, with one variant and with two, the replay over a
// board, and the chip with its one-time hint. The demo is a stand-in: this module sees no game's drawing, so every frame
// is the plain grid of the practice previews and only the captions change.

/** A demo that is only its captions. */
internal class StandInIntroDemo(override val variant: Int, override val captions: List<String>) : IntroDemo

private val Variants = listOf(
    IntroVariant(
        "Fill the line",
        "When a color sits inside one line, no other color can use that line.",
        "Find a color whose squares all share a row.",
        "Cross out the rest of that row.",
    ),
    IntroVariant(
        "Fill the color",
        "When every square left in a line is one color, that color is done elsewhere.",
        "Find a line with only one color left.",
        "Cross out that color everywhere else.",
    ),
)

private fun standInEntries(count: Int) = Variants.take(count).mapIndexed { index, variant ->
    IntroEntry(
        variant,
        StandInIntroDemo(
            index,
            listOf("Here is the position.", "The evidence lights up.", "The first square lands.", "The line is done."),
        ),
    )
}

/**
 * The watching screen as a still: [count] variants, variant 0 at [frame], on a pane with or without the [showBack] arrow;
 * [replay] is the copy over a live board.
 */
@Composable
internal fun WatchPreviewFrame(count: Int = 1, frame: Int = 1, replay: Boolean = false, showBack: Boolean = true) = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        WatchPane(
            chapter = standInChapter(board = 1),
            progress = TechniqueProgress(TechniqueProgress.Mark.Practicing, inRow = 2, answeredAtMs = 1L),
            showBack = showBack,
            onClose = {},
            entries = standInEntries(count),
            variant = 0,
            frame = frame,
            replay = replay,
            demo = { _, _, modifier -> StandInGrid(modifier) },
            onSelect = {},
            onSeek = {},
            onReplay = {},
            onLeave = {},
            onPrimary = {},
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )
    }
}

@PuzzleBoxPreviews
@Composable
private fun WatchPreview() = WatchPreviewFrame()

@PuzzleBoxPreviews
@Composable
private fun WatchTwoVariantsPreview() = WatchPreviewFrame(count = 2, frame = 3)

@PuzzleBoxPreviews
@Composable
private fun WatchReplayPreview() = WatchPreviewFrame(count = 2, frame = 3, replay = true)

@PuzzleBoxPreviews
@Composable
private fun HowItWorksChipPreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(Spacing.lg)) {
            PracticeHeader(standInChapter(), TechniqueProgress(TechniqueProgress.Mark.Practicing, 2, 1L), showBack = false, onClose = {}, howItWorks = HowItWorks({}, hint = false, {}))
        }
    }
}

@PuzzleBoxPreviews
@Composable
private fun HowItWorksHintPreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(Spacing.lg)) {
            PracticeHeader(standInChapter(), TechniqueProgress(TechniqueProgress.Mark.Practicing, 2, 1L), showBack = false, onClose = {}, howItWorks = HowItWorks({}, hint = true, {}))
        }
    }
}
