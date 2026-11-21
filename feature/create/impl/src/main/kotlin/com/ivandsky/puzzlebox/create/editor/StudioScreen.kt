package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.ivandsky.puzzlebox.create.importtext.ImportSheet
import com.ivandsky.puzzlebox.create.importtext.ImportSheetActions
import com.ivandsky.puzzlebox.create.importtext.ImportSheetUi
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.MaxContentWidth
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay
import com.ivandsky.puzzlebox.model.ShareCode

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
 * no pill. The card's Check again goes to [onCheckAgain], its witness choice ([overlay]) to
 * [onOverlay] and its Auto-show switch ([autoShow]) to [onAutoShow]. The title and the overflow's Rename open the [RenameSheet], whose Save reports
 * through [onRename]. While [testCleared] (a board edit invalidated the passed test) a toast
 * above the action area offers Undo ([onUndoTestClear]); it reports its timeout to [onTestClearedGone].
 * The [toast] slot shows the route's other notices in the same place. Once published
 * ([readOnly]) the board takes no touch or accessibility action, the bar offers no Rename or
 * Delete but Unpublish… ([onUnpublish], null once [unpublished]), and the tools give way to the
 * "Published · read-only" chip ("Unpublished · read-only" once taken down) with Duplicate
 * ([onDuplicate]). The overflow's Import from text ([onImport], null draws no item) opens the
 * [ImportSheet] over [importSheet], which reports through [importActions]; an [imported] draft
 * carries the Imported chip.
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
    onRollTitle: suspend () -> String,
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
    autoShow: Boolean = true,
    onAutoShow: (Boolean) -> Unit = {},
    issues: @Composable (Modifier) -> Unit = {},
    actionArea: @Composable (Modifier) -> Unit = {},
    testCleared: Boolean = false,
    onUndoTestClear: () -> Unit = {},
    onTestClearedGone: () -> Unit = {},
    toast: @Composable (Modifier) -> Unit = {},
    readOnly: Boolean = false,
    unpublished: Boolean = false,
    onUnpublish: (() -> Unit)? = null,
    imported: Boolean = false,
    onImport: (() -> Unit)? = null,
    importSheet: ImportSheetUi = ImportSheetUi(),
    importActions: ImportSheetActions = ImportSheetActions(),
) {
    var renaming by rememberSaveable { mutableStateOf(false) }
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    // A wide single pane keeps a readable width, centred; the two panes use the whole width.
    val width = if (layout == PlayLayout.SinglePane) Modifier.widthIn(max = MaxContentWidth) else Modifier
    val keyboard = rememberStudioKeyboard()
    Box(modifier.fillMaxSize().then(keyboard.measure), contentAlignment = Alignment.TopCenter) {
        Column(width.fillMaxSize()) {
            StudioTopBar(
                title = title,
                subtitle = subtitle,
                saving = saving,
                onBack = onBack,
                onRename = { renaming = true },
                onDuplicate = onDuplicate,
                onDelete = onDelete,
                readOnly = readOnly,
                onUnpublish = onUnpublish,
                onImport = onImport,
                imported = imported,
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
                            autoShow = autoShow,
                            onAutoShow = onAutoShow,
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
                autoShow = autoShow,
                onAutoShow = onAutoShow,
                board = { boardModifier -> if (readOnly) ReadOnlyBoard(board, boardModifier) else board(boardModifier) },
                toolStrip = { stripModifier -> ToolsOrPublished(readOnly, unpublished, toolStrip, onDuplicate, stripModifier) },
                issues = issues,
                actionArea = actionArea,
                keyboard = keyboard,
                toast = { toastModifier ->
                    TestClearedToast(shown = testCleared, onUndo = onUndoTestClear, onGone = onTestClearedGone, modifier = toastModifier)
                    toast(toastModifier)
                },
                modifier = Modifier.weight(1f),
                hugBoard = readOnly,
            )
        }
    }
    if (renaming) {
        RenameSheet(
            title = title,
            onSave = { next ->
                renaming = false
                onRename(next)
            },
            onDismiss = { renaming = false },
            onRoll = onRollTitle,
        )
    }
    ImportSheet(ui = importSheet, actions = importActions)
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
            onRename = {}, onRollTitle = { "Amber Falcon" },
            onDuplicate = {},
            onDelete = {},
            board = { PreviewBoard(it) },
            toolStrip = { stripModifier ->
                ToolStrip(
                    canUndo = true,
                    canRedo = false,
                    onUndo = {},
                    onRedo = {},
                    sizes = listOf(VariantId("queens/4"), VariantId("queens/5")),
                    size = VariantId("queens/4"),
                    sizeLabel = { "4×4" },
                    onResize = {},
                    onClear = {},
                    hint = { Text("Drag across cells, then pick a color", it, style = MaterialTheme.typography.bodySmall) },
                    modifier = stripModifier,
                )
            },
            actionArea = { areaModifier ->
                ActionArea(passed = null, canTestSolve = false, onTestSolve = {}, blocks = emptyList(), modifier = areaModifier)
            },
        )
    }
}

@PuzzleBoxPreviews
@Composable
private fun StudioScreenPublishedPreview() {
    PuzzleBoxTheme {
        StudioScreen(
            title = "Castle",
            subtitle = "Queens 4×4",
            saving = false,
            verdict = Verdict.Unique(Solution(emptyList())),
            layout = PlayLayout.SinglePane,
            onBack = {},
            onRename = {}, onRollTitle = { "Amber Falcon" },
            onDuplicate = {},
            onDelete = {},
            board = { PreviewBoard(it) },
            toolStrip = {},
            actionArea = { areaModifier ->
                val code = checkNotNull(ShareCode.parse("K7Q2MX"))
                PublishedTicket(ShareTicket(code, "Castle", "Ivan", 102_000, "Queens", AccentSlot.Violet), onCopied = {}, modifier = areaModifier)
            },
            readOnly = true,
        )
    }
}

@Composable
private fun PreviewBoard(modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(Modifier.aspectRatio(1f, matchHeightConstraintsFirst = true).background(MaterialTheme.colorScheme.surfaceContainerHighest, AppShapes.small))
    }
}
