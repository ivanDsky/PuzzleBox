package com.ivandsky.puzzlebox.games.strandsui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.AdaptivePlayScaffold
import com.ivandsky.puzzlebox.gameui.PlayBoardFallback
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayChrome
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.rememberReducedMotion
import com.ivandsky.puzzlebox.games.strands.StrandsRating
import com.ivandsky.puzzlebox.games.strandsui.chrome.FoundCounter
import com.ivandsky.puzzlebox.games.strandsui.chrome.HintModal
import com.ivandsky.puzzlebox.games.strandsui.chrome.StrandsHeaderLine
import com.ivandsky.puzzlebox.games.strandsui.chrome.StrandsHintControl
import com.ivandsky.puzzlebox.games.strandsui.chrome.ThemeCard
import com.ivandsky.puzzlebox.games.strandsui.chrome.WordSlotLine

/**
 * The Strands play screen (A37): the board pane stacks the theme card, the header line, the
 * word slot, the counter and the board; the shared chrome (clock, How to play) with the hint
 * control sits after it on a phone and beside it on a tablet. No undo and no shared hint
 * control (A15: the hint is Strands' own economy). A finished board stays on screen with its
 * rating word and hands its time and hints to [onSolved] once per completion. A tap anywhere
 * off the board drops a half-traced word, as on NYT.
 */
@Composable
internal fun StrandsPlayScreen(
    controller: StrandsPlayController,
    status: PlayBoardStatus,
    layout: PlayLayout,
    onRetry: () -> Unit,
    onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
    modifier: Modifier = Modifier,
    onHowToPlay: (() -> Unit)? = null,
) {
    // Keyed on completion, so a replay's next win hands off again.
    LaunchedEffect(controller.isComplete) {
        val solved = controller.session?.takeIf { it.state.isComplete } ?: return@LaunchedEffect
        onSolved(solved.elapsedMs, solved.state.hintsUsed)
    }
    val reduceMotion = rememberReducedMotion()
    val header = controller.header
    Box(modifier.fillMaxSize().clearTraceOnBackgroundTap(controller)) {
        if (status is PlayBoardStatus.Ready && header != null && layout == PlayLayout.SinglePane) {
            // One scroll for the whole phone page: 8 rows of 48 dp cells (SC11) plus the chrome
            // outgrow a landscape phone, and the scaffold's fixed side pane would leave the
            // board a sliver. NYT's page scrolls the same way.
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("strands.scroll")) {
                BoardColumn(controller, header, twoPane = false, reduceMotion)
                SidePanel(controller, status, reduceMotion, onHowToPlay)
            }
        } else {
            AdaptivePlayScaffold(
                layout = layout,
                modifier = Modifier.fillMaxSize(),
                board = { BoardPane(controller, status, reduceMotion, onRetry) },
                side = { SidePanel(controller, status, reduceMotion, onHowToPlay) },
            )
        }
    }
    controller.hintDialog?.let { dialog -> HintModal(dialog, controller::dismissHintDialog) }
}

/**
 * Runs after every child (pointer events bubble up): the board, buttons and the scroll
 * consume their own downs and drags, so only a clean tap on the background lands here.
 */
private fun Modifier.clearTraceOnBackgroundTap(controller: StrandsPlayController): Modifier =
    pointerInput(controller) {
        awaitEachGesture {
            awaitFirstDown()
            // An empty trace has nothing to drop; clearing it would wipe the verdict shown.
            if (waitForUpOrCancellation() != null && controller.trace.path.isNotEmpty()) {
                controller.onTrace(TraceInput.outside())
            }
        }
    }

@Composable
private fun BoardPane(
    controller: StrandsPlayController,
    status: PlayBoardStatus,
    reduceMotion: Boolean,
    onRetry: () -> Unit,
) {
    val header = controller.header
    if (status !is PlayBoardStatus.Ready || header == null) {
        Box(Modifier.fillMaxSize().testTag("strands.board_area"), contentAlignment = Alignment.Center) {
            PlayBoardFallback(status, onRetry, tagPrefix = "strands")
        }
        return
    }
    BoardColumn(controller, header, twoPane = true, reduceMotion, Modifier.fillMaxSize())
}

/** On a tablet pane the board's height is bound (A37), so it shrinks to fit beside the chrome. */
@Composable
private fun BoardColumn(
    controller: StrandsPlayController,
    header: StrandsHeader,
    twoPane: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val model = controller.model
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            .testTag("strands.board_area"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterVertically),
    ) {
        ThemeCard(
            clue = header.clue,
            rating = controller.solvedHints?.let(StrandsRating::of),
            modifier = Modifier.widthIn(max = BoardMaxWidth),
        )
        StrandsHeaderLine(header)
        WordSlotLine(
            slot = controller.slot,
            found = model.foundCount,
            total = model.totalCount,
            reduceMotion = reduceMotion,
            modifier = Modifier.widthIn(max = BoardMaxWidth),
        )
        FoundCounter(found = model.foundCount, total = model.totalCount)
        val boardHeight = if (twoPane) Modifier.weight(1f, fill = false) else Modifier
        StrandsBoard(controller, boardHeight.widthIn(max = BoardMaxWidth))
    }
}

@Composable
private fun SidePanel(
    controller: StrandsPlayController,
    status: PlayBoardStatus,
    reduceMotion: Boolean,
    onHowToPlay: (() -> Unit)?,
) {
    val ready = status as? PlayBoardStatus.Ready
    PlayChrome(
        title = StrandsCopy.TITLE,
        // "No. n" lives on the board pane's header line, next to the credit.
        subtitle = "Daily",
        clock = controller,
        hints = null,
        undo = null,
        banner = ready?.banner,
        onHowToPlay = onHowToPlay,
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        if (ready != null && controller.header != null) {
            StrandsHintControl(
                hint = controller.hint,
                onHint = controller::pressHint,
                reduceMotion = reduceMotion,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

/** NYT's board is 324 px wide; 360 dp leaves the letters room on a tablet pane. */
private val BoardMaxWidth = 360.dp
