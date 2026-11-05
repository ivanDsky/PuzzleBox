package com.ivandsky.puzzlebox.games.strandsui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AppDialog
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.AdaptivePlayScaffold
import com.ivandsky.puzzlebox.gameui.PlayBoardFallback
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayChrome
import com.ivandsky.puzzlebox.gameui.PlayLayout

/**
 * The Strands play screen: clue, word slot and board in the board pane, the shared chrome
 * (clock, How to play) beside it with the hint button and the found counter as the game's
 * controls. No undo and no shared hint control (A15: the hint is Strands' own economy).
 * A finished board hands its time and hints to [onSolved] once per completion.
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
    Box(modifier.fillMaxSize()) {
        AdaptivePlayScaffold(
            layout = layout,
            modifier = Modifier.fillMaxSize(),
            board = { BoardArea(controller, status, onRetry) },
            side = { SidePanel(controller, status, onHowToPlay) },
        )
    }
    controller.hintDialog?.let { dialog -> StrandsHintDialog(dialog, controller::dismissHintDialog) }
}

@Composable
private fun BoxScope.BoardArea(controller: StrandsPlayController, status: PlayBoardStatus, onRetry: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .testTag("strands.board_area"),
        contentAlignment = Alignment.Center,
    ) {
        val header = controller.header
        if (status is PlayBoardStatus.Ready && header != null) {
            Column(
                Modifier.padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                StrandsClue(header)
                StrandsSlotLine(controller.slot)
                StrandsBoard(controller, Modifier.widthIn(max = BoardMaxWidth))
            }
        } else {
            PlayBoardFallback(status, onRetry, tagPrefix = "strands")
        }
    }
}

@Composable
internal fun StrandsClue(header: StrandsHeader) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = StrandsCopy.THEME_LABEL,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = header.clue,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("strands.clue"),
        )
        Text(
            text = header.credit,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Fixed height, so the board never jumps as letters come and go. */
@Composable
internal fun StrandsSlotLine(slot: WordSlot) {
    val text = when (slot) {
        WordSlot.Empty -> ""
        is WordSlot.Tracing -> slot.letters
        is WordSlot.Verdict -> slot.copy
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SlotHeight)
            .testTag("strands.slot"),
    )
}

@Composable
private fun SidePanel(controller: StrandsPlayController, status: PlayBoardStatus, onHowToPlay: (() -> Unit)?) {
    val ready = status as? PlayBoardStatus.Ready
    PlayChrome(
        title = StrandsCopy.TITLE,
        subtitle = ready?.let { controller.header?.puzzleNumber }?.let(StrandsCopy::number) ?: "Daily",
        clock = controller,
        hints = null,
        undo = null,
        banner = ready?.banner,
        onHowToPlay = onHowToPlay,
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        if (controller.header != null) {
            StrandsControls(
                found = controller.model.foundCount,
                total = controller.model.totalCount,
                hint = controller.hint,
                onHint = controller::pressHint,
            )
        }
    }
}

/** The counter and the hint button. The button stays pressable: with nothing earned it explains why (A7). */
@Composable
internal fun StrandsControls(found: Int, total: Int, hint: HintUi, onHint: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = StrandsCopy.found(found, total),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("strands.counter"),
        )
        val label = StrandsCopy.HINT
        val buttonModifier = Modifier.testTag("strands.hint")
        if (hint.available) {
            PrimaryButton(text = label, onClick = onHint, modifier = buttonModifier)
        } else {
            SecondaryButton(text = label, onClick = onHint, modifier = buttonModifier)
        }
    }
}

@Composable
internal fun StrandsHintDialog(dialog: HintDialog, onDismiss: () -> Unit) {
    AppDialog(
        title = StrandsCopy.HINT,
        onDismissRequest = onDismiss,
        content = {
            Text(
                text = when (dialog) {
                    HintDialog.OnBoard -> StrandsCopy.HINT_ON_BOARD
                    HintDialog.NoneAvailable -> StrandsCopy.HINT_NONE_AVAILABLE
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("strands.hint_dialog"),
            )
        },
        actions = { PrimaryButton(text = StrandsCopy.DISMISS, onClick = onDismiss) },
    )
}

/** NYT's board is 324 px wide; 360 dp leaves the letters room on a tablet pane. */
private val BoardMaxWidth = 360.dp
private val SlotHeight = 32.dp
