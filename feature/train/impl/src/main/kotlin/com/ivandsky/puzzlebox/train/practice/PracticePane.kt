package com.ivandsky.puzzlebox.train.practice

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.gameui.trainer.PracticeBoard
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus

/** A pane narrower than this wraps the done band's Next board button under its text, which needs the taller band. */
private val NarrowPane = 560.dp

/** The done band's height when its button wraps under the text. */
private val WrappedDoneBandHeight = 136.dp

private const val GlowMillis = 200

/**
 * A technique's practice (prototype 03, variant A): the [chapter]'s technique name with its in-a-row [progress] and the
 * board's number as the title row, then by [state] a spinner while a board is cut or a puzzle loads, the empty message,
 * the needs-a-connection or can't-load message, the chapter's result page, or the board. The board is the game's own
 * ([board], which draws its Spotlight itself) under one status band attached to its top edge, under the chapter's steps,
 * then the technique's idea and Show me. A finished board is celebrated: its band, steps, glow and stamp say so, and
 * [hold] counts down to the next board, which [onSkip] moves to at once. The result page takes the whole pane; its
 * [onNextChapter], [onPracticeAgain] and [onClose] (Back to techniques) are the only ways on. [showBack] adds a back arrow
 * to the title row and the result page (the phone, where the board covers the list); [onClose] is also what the empty
 * message's action does. [onTryAgain] is the needs-a-connection action, which fetches a puzzle, and [onTryAnother] the
 * can't-load action, which opens a puzzle already on the device. With [intro] the technique's intro shows before board 1
 * and again, over the board, from the header's "How it works" chip, which [intro] also gives its one-time hint.
 */
@Composable
internal fun PracticePane(
    chapter: PracticeChapter,
    state: PracticeState,
    progress: TechniqueProgress,
    showBack: Boolean,
    onClose: () -> Unit,
    board: @Composable (PracticeBoard, Modifier) -> Unit,
    onTryAgain: () -> Unit,
    onTryAnother: () -> Unit,
    hold: BoardHold?,
    onSkip: () -> Unit,
    onNextChapter: () -> Unit,
    onPracticeAgain: () -> Unit,
    modifier: Modifier = Modifier,
    intro: IntroControls? = null,
) {
    val name = chapter.technique.name
    val watched = when {
        intro == null -> null
        state is PracticeState.Intro -> state.intro
        state is PracticeState.Practicing && intro.replaying -> intro.intro
        else -> null
    }
    Box(modifier.fillMaxSize().testTag("train.practice")) {
        if (state is PracticeState.Result) {
            ChapterResultPage(state.result, showBack, onNextChapter, onPracticeAgain, onClose)
        } else if (watched != null) {
            IntroWatch(chapter, progress, showBack, onClose, watched, replay = state !is PracticeState.Intro, intro!!)
        } else {
            Column(Modifier.fillMaxSize().padding(bottom = Spacing.lg)) {
                val live = (state as? PracticeState.Practicing)?.board
                // The chip is for a board in play: not while it is done, and only for a technique that has an intro.
                val ended = live?.let { it.status.collectAsStateWithLifecycle().value is PracticeStatus.Done } == true
                val chip = if (intro?.intro != null && live != null && !ended) HowItWorks(intro.onShow, intro.hint, intro.onDismissHint) else null
                PracticeHeader(chapter, progress, showBack, onClose, howItWorks = chip)
                Spacer(Modifier.size(Spacing.sm))
                when (state) {
                    PracticeState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    PracticeState.Empty -> EmptyPage(name, onClose)
                    PracticeState.NeedsConnection -> NeedsConnectionPage(name, onTryAgain)
                    is PracticeState.CantLoad -> CantLoadPage(name, onTryAnother)
                    // One key per board, so the next board starts from its own state.
                    is PracticeState.Practicing -> key(state.board) { PracticeBody(chapter, state.board, board, hold, onSkip) }
                    is PracticeState.Intro, is PracticeState.Result -> Unit
                }
            }
        }
    }
}

/** One board: the chapter's steps, the status band over the game's board, the idea and Show me. */
@Composable
private fun PracticeBody(
    chapter: PracticeChapter,
    board: PracticeBoard,
    boardContent: @Composable (PracticeBoard, Modifier) -> Unit,
    hold: BoardHold?,
    onSkip: () -> Unit,
) {
    val status by board.status.collectAsStateWithLifecycle()
    val done = status is PracticeStatus.Done
    Column(Modifier.fillMaxSize()) {
        BoardSteps(chapter.board, chapter.boards, currentDone = done, Modifier.padding(top = 6.dp, bottom = Spacing.md))
        // The band and the board are one frame, centered in what the idea and Show me leave.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val narrow = maxWidth < NarrowPane
            BandOverBoard(
                band = {
                    if (done) DoneBand(chapter, hold, narrow, onSkip) else StatusBand(status, chapter, onGotIt = board::gotIt)
                },
                board = { boardContent(board, Modifier) },
                modifier = Modifier.glow(done),
                bandHeight = if (done && narrow) WrappedDoneBandHeight else BandHeight,
                overlay = { if (done) BoardEndStamp() },
            )
        }
        Spacer(Modifier.size(Spacing.md))
        IdeaBlock(chapter.technique.line)
        Spacer(Modifier.size(Spacing.md))
        val refused = status is PracticeStatus.Easier || status is PracticeStatus.Shown || done
        SecondaryButton("Show me", onClick = board::showMe, Modifier.testTag("train.showMe"), enabled = !refused)
    }
}

/** The finished board's glow: a primary ring and a soft primary shadow that come on over [GlowMillis]; at once under reduced motion. */
@Composable
private fun Modifier.glow(on: Boolean): Modifier {
    val primary = MaterialTheme.colorScheme.primary
    val spec = if (rememberReducedMotion()) snap() else tween<Float>(GlowMillis)
    val level by animateFloatAsState(if (on) 1f else 0f, spec, label = "boardGlow")
    val shape = MaterialTheme.shapes.medium
    val reach: Dp = GlowReach * level
    return this
        .shadow(reach, shape, clip = false, ambientColor = primary.copy(alpha = 0.5f), spotColor = primary.copy(alpha = 0.5f))
        .border(2.dp, primary.copy(alpha = level), shape)
}

private val GlowReach = 12.dp
