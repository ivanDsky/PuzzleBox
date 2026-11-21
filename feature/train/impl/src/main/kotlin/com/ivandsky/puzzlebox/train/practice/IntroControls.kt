package com.ivandsky.puzzlebox.train.practice

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.gameui.trainer.IntroDemo

/**
 * What the practice pane needs to show the technique intro (spec §2.7): the technique's [intro] (null when it has none,
 * which also hides the "How it works" chip), whether it is being [replaying] over a live board, whether the one-time
 * [hint] bubble is up, the game's [demo] drawing of a frame, and the actions: [onShow] (the chip), [onClose] (leave a
 * replay), [onStart] (Skip and Start practice, which open board 1) and [onDismissHint].
 */
internal class IntroControls(
    val intro: TechniqueIntro?,
    val replaying: Boolean,
    val hint: Boolean,
    val demo: @Composable (IntroDemo, Int, Modifier) -> Unit,
    val onShow: () -> Unit,
    val onClose: () -> Unit,
    val onStart: () -> Unit,
    val onDismissHint: () -> Unit,
)
