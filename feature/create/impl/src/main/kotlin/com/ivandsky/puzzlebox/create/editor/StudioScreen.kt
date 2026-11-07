package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay

/** Below this screen width the single-pane card is a bottom sheet (Material's compact width). */
private const val SheetBelowDp = 600

/** Below this screen width the pill shows its glyph alone, so the title keeps the bar's width. */
private const val GlyphPillBelowDp = 360

/**
 * The Studio: the [StudioTopBar] (Back, the draft's [title] over its [subtitle] and the
 * autosave's [saving] state, the overflow) over the [StudioLayout]: the game's [board], the
 * [toolStrip] and the [actionArea] (test status, Test solve).
 * On a phone ([PlayLayout.SinglePane]) the bar carries the [verdict] pill ([onShowCard]); while
 * [cardOpen] the card shows as a half-expanded bottom sheet on a compact width and as a popover
 * over the pill on a wider one, the game's fix list ([issues]) inside it, closing through
 * [onDismissCard]. In [PlayLayout.TwoPane] the card sits inline in the side pane and there is
 * no pill. The card's Check again goes to [onCheckAgain] and its witness choice ([overlay]) to
 * [onOverlay]. The title and the overflow's Rename open the [RenameSheet], whose Save reports
 * through [onRename]. While [testCleared] (a board edit invalidated the passed test) a toast
 * above the action area offers Undo ([onUndoTestClear]); it reports its timeout to [onTestClearedGone].
 * The [toast] slot shows the route's other notices in the same place.
 */
@Composable
fun StudioScreen(
    title: String,
    subtitle: String,
    saving: Boolean,
    verdict: Verdict,
    layout: PlayLayout,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    board: @Composable (Modifier) -> Unit,
    toolStrip: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    cardOpen: Boolean = false,
    overlay: EditorOverlay = EditorOverlay(),
    onShowCard: () -> Unit = {},
    onDismissCard: () -> Unit = {},
    onCheckAgain: () -> Unit = {},
    onOverlay: (EditorOverlay) -> Unit = {},
    issues: @Composable (Modifier) -> Unit = {},
    actionArea: @Composable (Modifier) -> Unit = {},
    testCleared: Boolean = false,
    onUndoTestClear: () -> Unit = {},
    onTestClearedGone: () -> Unit = {},
    toast: @Composable (Modifier) -> Unit = {},
) {
    var renaming by rememberSaveable { mutableStateOf(false) }
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    Column(modifier.fillMaxSize()) {
        StudioTopBar(
            title = title,
            subtitle = subtitle,
            saving = saving,
            onBack = onBack,
            onRename = { renaming = true },
            onDuplicate = onDuplicate,
            onDelete = onDelete,
            pill = {
                if (layout == PlayLayout.SinglePane) {
                    PillWithCard(
                        verdict = verdict,
                        compactPill = screenWidthDp < GlyphPillBelowDp,
                        asSheet = screenWidthDp < SheetBelowDp,
                        cardOpen = cardOpen,
                        overlay = overlay,
                        onShow = onShowCard,
                        onDismiss = onDismissCard,
                        onCheckAgain = onCheckAgain,
                        onOverlay = onOverlay,
                        issues = issues,
                    )
                }
            },
        )
        StudioLayout(
            layout = layout,
            verdict = verdict,
            overlay = overlay,
            onCheckAgain = onCheckAgain,
            onOverlay = onOverlay,
            board = board,
            toolStrip = toolStrip,
            issues = issues,
            actionArea = actionArea,
            toast = { toastModifier ->
                TestClearedToast(shown = testCleared, onUndo = onUndoTestClear, onGone = onTestClearedGone, modifier = toastModifier)
                toast(toastModifier)
            },
            modifier = Modifier.weight(1f),
        )
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

@PuzzleBoxPreviews
@Composable
private fun StudioScreenPreview() {
    PuzzleBoxTheme {
        StudioScreen(
            title = "Untitled 1",
            subtitle = "Queens 4×4",
            saving = false,
            verdict = Verdict.Incomplete(IncompleteReason("queens.unpainted", 16)),
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
            actionArea = { areaModifier ->
                ActionArea(passed = null, canTestSolve = false, onTestSolve = {}, blocks = emptyList(), modifier = areaModifier)
            },
        )
    }
}
