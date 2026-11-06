package com.ivandsky.puzzlebox.games.strandsui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.AdaptivePlayScaffold
import com.ivandsky.puzzlebox.gameui.PlayBoardFallback
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayChrome
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.games.strands.StrandsRating
import com.ivandsky.puzzlebox.games.strandsui.board.strandsCellSizeFor
import com.ivandsky.puzzlebox.games.strandsui.chrome.HintModal
import com.ivandsky.puzzlebox.games.strandsui.chrome.StrandsHeaderLine
import com.ivandsky.puzzlebox.games.strandsui.chrome.StrandsHintRow
import com.ivandsky.puzzlebox.games.strandsui.chrome.ThemeCard
import com.ivandsky.puzzlebox.games.strandsui.chrome.WordSlotLine
import com.ivandsky.puzzlebox.games.strandsui.chrome.FoundWordsList

/**
 * The Strands play screen (A37). On a phone one column stacks the theme card, the header
 * line, the word slot, the board and the hint row under it (counter and Hint, as on NYT),
 * then the shared chrome (banner and caption; title, clock and How to play are in the
 * route's top bar). On a tablet the board pane keeps only
 * the board and its hint row, so the board takes the pane's full height; the theme card,
 * header line, word slot and the found-words list move into the side panel under the shared
 * chrome. No undo and no shared hint control (A15: the hint is Strands' own economy). A
 * finished board stays on screen with its rating word and hands its time and hints to
 * [onSolved] once per completion. A tap anywhere off the board drops a half-traced word.
 */
@Composable
internal fun StrandsPlayScreen(
    controller: StrandsPlayController,
    status: PlayBoardStatus,
    layout: PlayLayout,
    onRetry: () -> Unit,
    onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
    modifier: Modifier = Modifier,
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
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val viewport = maxHeight
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("strands.scroll")) {
                    BoardColumn(controller, header, reduceMotion, fitHeight = viewport, withChrome = true)
                    SidePanel(controller, status, reduceMotion, withChrome = false)
                }
            }
        } else {
            AdaptivePlayScaffold(
                layout = layout,
                modifier = Modifier.fillMaxSize(),
                board = { BoardPane(controller, status, reduceMotion, onRetry) },
                side = { SidePanel(controller, status, reduceMotion, withChrome = true) },
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
    BoardColumn(controller, header, reduceMotion, fitHeight = null, withChrome = false, Modifier.fillMaxSize())
}

/**
 * With [withChrome], the [PuzzleChrome] above, then the board, then the hint row as wide as
 * the board (NYT's grid and row under it). The board takes the height the rest leaves (A37):
 * the tablet pane's height, or on a phone [fitHeight], the viewport the page scrolls in, so
 * its cells shrink toward the 48 dp floor before the hint row is pushed off screen.
 */
@Composable
private fun BoardColumn(
    controller: StrandsPlayController,
    header: StrandsHeader,
    reduceMotion: Boolean,
    fitHeight: Dp?,
    withChrome: Boolean,
    modifier: Modifier = Modifier,
) {
    val model = controller.model
    Layout(
        contents = listOf(
            { if (withChrome) PuzzleChrome(controller, header, reduceMotion) },
            { StrandsBoard(controller) },
            {
                StrandsHintRow(
                    found = model.foundCount,
                    total = model.totalCount,
                    // A solved board has no hint to give (NYT drops it too).
                    hint = controller.hint.takeUnless { controller.isComplete },
                    onHint = controller::pressHint,
                    reduceMotion = reduceMotion,
                )
            },
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = BoardColumnPadding)
            .testTag("strands.board_area"),
    ) { (top, board, row), constraints ->
        val width = constraints.maxWidth
        val gap = Spacing.sm.roundToPx()
        // The widest the board can be; the chrome above and the row's height are set by it.
        val widest = if (model.rows == 0) width else {
            (strandsCellSizeFor(width.toDp(), Dp.Infinity, model.rows, model.cols) * model.cols).roundToPx().coerceAtMost(width)
        }
        val topPlaceable = top.firstOrNull()?.measure(Constraints(maxWidth = widest))
        val topHeight = topPlaceable?.let { it.height + gap } ?: 0
        val rowHeight = row.single().maxIntrinsicHeight(widest)
        val fit = when {
            constraints.hasBoundedHeight -> constraints.maxHeight
            fitHeight != null && fitHeight.isFinite -> (fitHeight - BoardColumnPadding * 2).roundToPx()
            else -> null
        }
        val boardBudget = fit?.let { (it - topHeight - rowHeight - gap).coerceAtLeast(0).toDp() } ?: Dp.Infinity
        val cell = if (model.rows == 0) 0.dp else strandsCellSizeFor(width.toDp(), boardBudget, model.rows, model.cols)
        val boardWidth = (cell * model.cols).roundToPx().coerceAtMost(width).takeIf { it > 0 } ?: widest
        val boardPlaceable = board.single().measure(Constraints.fixed(width, (cell * model.rows).roundToPx()))
        val rowPlaceable = row.single().measure(Constraints.fixedWidth(boardWidth))
        val total = topHeight + boardPlaceable.height + gap + rowPlaceable.height
        val height = total.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            // A tablet pane is taller than the stack: centre it, as the old column did.
            var y = ((height - total) / 2).coerceAtLeast(0)
            for (placeable in listOfNotNull(topPlaceable, boardPlaceable, rowPlaceable)) {
                placeable.placeRelative((width - placeable.width) / 2, y)
                y += placeable.height + gap
            }
        }
    }
}

/** The puzzle's own chrome: the theme card, the header line and the word slot. */
@Composable
private fun PuzzleChrome(controller: StrandsPlayController, header: StrandsHeader, reduceMotion: Boolean) {
    val model = controller.model
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ThemeCard(header.clue, controller.solvedHints?.let(StrandsRating::of), Modifier.fillMaxWidth())
        StrandsHeaderLine(header)
        WordSlotLine(controller.slot, model.foundCount, model.totalCount, reduceMotion, Modifier.fillMaxWidth())
    }
}

/**
 * The shared chrome (the degrade banner and caption; the title, clock and How to play sit
 * in the route's top bar); [withChrome] (a tablet) adds the puzzle's chrome and the
 * found-words list under it.
 */
@Composable
private fun SidePanel(
    controller: StrandsPlayController,
    status: PlayBoardStatus,
    reduceMotion: Boolean,
    withChrome: Boolean,
) {
    val ready = status as? PlayBoardStatus.Ready
    val header = controller.header.takeIf { withChrome && ready != null }
    Column(Modifier.testTag("strands.side_panel").then(if (withChrome) Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
        PlayChrome(
            clock = controller,
            hints = null,
            undo = null,
            clear = null,
            banner = ready?.banner,
        )
        if (header != null) {
            Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
                PuzzleChrome(controller, header, reduceMotion)
                FoundWordsList(controller.foundWords, controller.model.totalCount, Modifier.padding(top = Spacing.sm))
            }
        }
    }
}

/** Keeps the hint row clear of the screen edge without costing the board a row of cells. */
private val BoardColumnPadding = Spacing.sm
