package com.ivandsky.puzzlebox.create.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.PlayLayout

private const val CardEnterScale = 0.9f

/**
 * The Studio: the [StudioTopBar] (Back, the draft's [title] over its [subtitle], the [verdict]
 * pill, the overflow) over the game's [board], drawn as the largest square that fits, with the
 * [toolStrip] under it. In [PlayLayout.TwoPane] the square sits centred in the space; on a
 * phone it hangs from the bar. The pill opens [verdictCard]'s card as a popover anchored at
 * the pill's top-right; the title and the overflow's Rename open the [RenameSheet], whose Save
 * reports through [onRename].
 */
@Composable
fun StudioScreen(
    title: String,
    subtitle: String,
    verdict: Verdict,
    verdictCard: VerdictCardState,
    layout: PlayLayout,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    board: @Composable (Modifier) -> Unit,
    toolStrip: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    var renaming by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        StudioTopBar(
            title = title,
            subtitle = subtitle,
            onBack = onBack,
            onRename = { renaming = true },
            onDuplicate = onDuplicate,
            onDelete = onDelete,
            pill = { PillWithCard(verdict = verdict, verdictCard = verdictCard) },
        )
        BoardSlot(
            alignment = if (layout == PlayLayout.TwoPane) Alignment.Center else Alignment.TopCenter,
            board = board,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(Spacing.lg),
        )
        toolStrip(Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg))
    }
    if (renaming) {
        RenameSheet(
            title = title,
            onSave = { next ->
                renaming = false
                onRename(next)
            },
            onDismiss = { renaming = false },
        )
    }
}

/** The verdict pill with its card's popover anchored at the pill's top-right. */
@Composable
private fun PillWithCard(verdict: Verdict, verdictCard: VerdictCardState) {
    Box {
        VerdictPill(verdict = verdict, onClick = verdictCard::show)
        VerdictCardPopover(verdict = verdict, state = verdictCard)
    }
}

/**
 * The card over the pill, its top-right on the pill's: an outside tap or Back dismisses it,
 * instantly. Motion: origin-aware **Scale in** from 0.9 + **Fade in** at the top-right corner,
 * MotionTokens medium, emphasized decelerate; under reduced motion a plain 120 ms fade.
 */
@Composable
private fun VerdictCardPopover(verdict: Verdict, state: VerdictCardState) {
    val open by state.open.collectAsStateWithLifecycle()
    if (!open) return
    val reducedMotion = rememberReducedMotion()
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    Popup(
        alignment = Alignment.TopEnd,
        onDismissRequest = state::dismiss,
        properties = PopupProperties(focusable = true),
    ) {
        AnimatedVisibility(
            visibleState = shown,
            enter = if (reducedMotion) {
                fadeIn(tween(MotionTokens.ReducedFadeDuration, easing = MotionTokens.EasingStandard))
            } else {
                val spec = tween<Float>(MotionTokens.DurationMedium, easing = MotionTokens.EasingEmphasizedDecelerate)
                scaleIn(spec, CardEnterScale, TransformOrigin(1f, 0f)) + fadeIn(spec)
            },
        ) {
            VerdictCard(verdict = verdict, onCheckAgain = state::checkAgain)
        }
    }
}

/** The square the game's board fills: the smaller of the slot's width and height. */
@Composable
private fun BoardSlot(
    alignment: Alignment,
    board: @Composable (Modifier) -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier, contentAlignment = alignment) {
        val side = minOf(maxWidth, maxHeight)
        Box(Modifier.size(side).testTag("create.editor.board")) {
            board(Modifier.fillMaxSize())
        }
    }
}

@PuzzleBoxPreviews
@Composable
private fun StudioScreenPreview() {
    PuzzleBoxTheme {
        StudioScreen(
            title = "Untitled 1",
            subtitle = "Queens 4×4",
            verdict = Verdict.Incomplete(IncompleteReason("queens.unpainted", 16)),
            verdictCard = VerdictCardState(recheck = {}),
            layout = PlayLayout.SinglePane,
            onBack = {},
            onRename = {},
            onDuplicate = {},
            onDelete = {},
            board = { boardModifier ->
                Box(boardModifier.background(MaterialTheme.colorScheme.surfaceContainerHighest, AppShapes.small))
            },
            toolStrip = { stripModifier ->
                ToolStrip(
                    canUndo = true,
                    canRedo = false,
                    onUndo = {},
                    onRedo = {},
                    sizes = listOf(VariantId("queens/4")),
                    size = VariantId("queens/4"),
                    sizeLabel = { "4×4" },
                    onResize = {},
                    onClear = {},
                    hint = { Text("Tap a cell to cycle its region colour", it, style = MaterialTheme.typography.bodySmall) },
                    modifier = stripModifier,
                )
            },
        )
    }
}
