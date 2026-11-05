package com.ivandsky.puzzlebox.games.strandsui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * rating word and hands its time and hints to [onSolved] once per completion.
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
    Box(modifier.fillMaxSize()) {
        AdaptivePlayScaffold(
            layout = layout,
            modifier = Modifier.fillMaxSize(),
            board = { BoardPane(controller, status, layout, reduceMotion, onRetry) },
            side = { SidePanel(controller, status, reduceMotion, onHowToPlay) },
        )
    }
    controller.hintDialog?.let { dialog -> HintModal(dialog, controller::dismissHintDialog) }
}

@Composable
private fun BoardPane(
    controller: StrandsPlayController,
    status: PlayBoardStatus,
    layout: PlayLayout,
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
    // A phone column scrolls, so the board keeps its 48 dp cells (SC11) on a short window;
    // a tablet pane bounds the board's height instead (A37), so it shrinks to fit.
    val twoPane = layout == PlayLayout.TwoPane
    val scroll = if (twoPane) Modifier else Modifier.verticalScroll(rememberScrollState())
    val model = controller.model
    Column(
        Modifier
            .fillMaxSize()
            .then(scroll)
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
