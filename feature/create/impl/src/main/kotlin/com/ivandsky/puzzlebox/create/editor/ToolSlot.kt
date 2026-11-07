package com.ivandsky.puzzlebox.create.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.domain.PublishOutcome
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Verdict

/**
 * The Studio's tool slot, under the board on a phone and in the tablet's side pane: the [tools], or, while [open] and not [editBoard],
 * the publish details [form] with an Edit board chip ([onEditBoard]) that brings the tools back.
 * While [open] the slot holds the taller of the two, so the board above keeps its size across
 * Edit board and back; once the details no longer apply it fits the tools again. Faded-out
 * content leaves the composition.
 *
 * Motion: tools and form **Crossfade** in place and the slot's height follows (DurationMedium
 * on the standard curve); under reduced motion, and when the Studio opens on the details, they
 * swap at once.
 */
@Composable
fun ToolSlot(
    open: Boolean,
    editBoard: Boolean,
    onEditBoard: () -> Unit,
    tools: @Composable (Modifier) -> Unit,
    form: @Composable (modifier: Modifier, editBoard: @Composable () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val details = open && !editBoard
    val reduced = rememberReducedMotion()
    val fade = tween<Float>(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard)
    // Until the tools have been measured there is no height of theirs to hold, so they compose once.
    var toolsHeight by remember { mutableIntStateOf(UNMEASURED) }
    var formHeight by remember { mutableIntStateOf(0) }
    var toolsSeen by remember { mutableStateOf(!details) }
    SideEffect { if (!details) toolsSeen = true }
    val resize = if (reduced) Modifier else Modifier.animateContentSize(tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard))
    Layout(
        content = {
            AnimatedVisibility(
                visible = !details || toolsHeight == UNMEASURED,
                modifier = Modifier.layoutId(TOOLS),
                enter = if (reduced) EnterTransition.None else fadeIn(fade),
                exit = if (reduced || !toolsSeen) ExitTransition.None else fadeOut(fade),
            ) {
                tools(Modifier.fillMaxWidth())
            }
            AnimatedVisibility(
                visible = details,
                modifier = Modifier.layoutId(FORM),
                enter = if (reduced) EnterTransition.None else fadeIn(fade),
                exit = if (reduced) ExitTransition.None else fadeOut(fade),
            ) {
                form(Modifier.fillMaxWidth()) { EditBoardChip(onEditBoard) }
            }
        },
        modifier = modifier.then(resize).testTag("create.tool.slot"),
    ) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0)
        val shownTools = measurables.firstOrNull { it.layoutId == TOOLS }?.measure(loose)
        val shownForm = measurables.firstOrNull { it.layoutId == FORM }?.measure(loose)
        if (shownTools != null && shownTools.height > 0) toolsHeight = shownTools.height
        if (!open) formHeight = 0 else if (shownForm != null && shownForm.height > formHeight) formHeight = shownForm.height
        val height = maxOf(toolsHeight, formHeight, 0).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(constraints.maxWidth, height) {
            shownTools?.place(0, 0)
            shownForm?.place(0, 0)
        }
    }
}

private const val TOOLS = "tools"
private const val FORM = "form"
private const val UNMEASURED = -1

/**
 * Whether the publish details are open: the Studio can publish ([publish] is its state), the
 * settled [verdict] is Unique, its test [passed], and the puzzle isn't published yet.
 */
internal fun detailsOpen(verdict: Verdict, passed: TestSolve?, publish: PublishSession.State?): Boolean =
    publish != null && verdict is Verdict.Unique && passed != null && publish.outcome !is PublishOutcome.Published

/** Brings the tools back; full-round like the Studio's verdict pill. */
@Composable
private fun EditBoardChip(onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text("Edit board", style = MaterialTheme.typography.labelLarge) },
        shape = PillShape,
        modifier = Modifier.testTag("create.tool.editBoard"),
    )
}

/** Edit board: the tools back in the held slot. */
@PuzzleBoxPreviews
@Composable
private fun ToolSlotToolsPreview() {
    PuzzleBoxTheme {
        ToolSlot(
            open = true,
            editBoard = true,
            onEditBoard = {},
            tools = { PreviewTools(it) },
            form = { formModifier, editBoard -> PreviewForm(formModifier, editBoard) },
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
        )
    }
}

/** Ready to publish: the details in the tools' place, Edit board on the note row. */
@PuzzleBoxPreviews
@Composable
private fun ToolSlotDetailsPreview() {
    PuzzleBoxTheme {
        ToolSlot(
            open = true,
            editBoard = false,
            onEditBoard = {},
            tools = { PreviewTools(it) },
            form = { formModifier, editBoard -> PreviewForm(formModifier, editBoard) },
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
        )
    }
}

@Composable
private fun PreviewTools(modifier: Modifier) {
    ToolStrip(
        canUndo = true,
        canRedo = false,
        onUndo = {},
        onRedo = {},
        sizes = listOf(VariantId("queens/6")),
        size = VariantId("queens/6"),
        sizeLabel = { "6×6" },
        onResize = {},
        onClear = {},
        hint = { Text("Crown | Paint", it, style = MaterialTheme.typography.bodySmall) },
        modifier = modifier,
    )
}

@Composable
private fun PreviewForm(modifier: Modifier, editBoard: @Composable () -> Unit) {
    PublishDetails(
        title = "Castle",
        details = PublishSession.Details(authorName = "Ivan"),
        signedIn = true,
        onRename = {},
        onAuthorName = {},
        onNote = {},
        modifier = modifier,
        paired = true,
        action = editBoard,
    )
}
