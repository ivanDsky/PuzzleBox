package com.ivandsky.puzzlebox.create.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.create.importtext.ImportSheet
import com.ivandsky.puzzlebox.create.importtext.ImportSheetActions
import com.ivandsky.puzzlebox.create.importtext.ImportSheetUi
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.AppTextButton
import com.ivandsky.puzzlebox.designsystem.MaxContentWidth
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
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
 * no pill. The card's Check again goes to [onCheckAgain] and its witness choice ([overlay]) to
 * [onOverlay]. The title and the overflow's Rename open the [RenameSheet], whose Save reports
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
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
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
                board = { boardModifier -> if (readOnly) ReadOnlyBoard(board, boardModifier) else board(boardModifier) },
                toolStrip = { stripModifier -> ToolsOrPublished(readOnly, unpublished, toolStrip, onDuplicate, stripModifier) },
                issues = issues,
                actionArea = actionArea,
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
        )
    }
    ImportSheet(ui = importSheet, actions = importActions)
}

/**
 * The tools while the draft is editable; once published, the "Published · read-only" chip with
 * Duplicate ([onDuplicate]), the one way on to an editable board; "Unpublished · read-only"
 * once [unpublished]. The two cross-fade.
 */
@Composable
private fun ToolsOrPublished(readOnly: Boolean, unpublished: Boolean, tools: @Composable (Modifier) -> Unit, onDuplicate: () -> Unit, modifier: Modifier) {
    val reduced = rememberReducedMotion()
    val duration = if (reduced) MotionTokens.ReducedFadeDuration else MotionTokens.DurationMedium
    AnimatedContent(
        targetState = readOnly,
        modifier = modifier,
        transitionSpec = { ContentTransform(fadeIn(tween(duration)), fadeOut(tween(duration)), sizeTransform = null) },
        label = "toolsOrPublished",
    ) { published ->
        if (!published) {
            tools(Modifier.fillMaxWidth())
            return@AnimatedContent
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, PillShape)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                    .testTag("create.published.readOnly"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                val tint = MaterialTheme.colorScheme.onSurfaceVariant
                if (!unpublished) Icon(PuzzleIcons.Check, contentDescription = null, modifier = Modifier.size(ChipMarkSize), tint = tint)
                Text(if (unpublished) "Unpublished · read-only" else "Published · read-only", style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            AppTextButton(text = "Duplicate", onClick = onDuplicate, modifier = Modifier.testTag("create.published.duplicate"))
        }
    }
}

private val ChipMarkSize = 16.dp

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
            board = { PreviewBoard(it) },
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
                    hint = { Text("Drag across cells, then pick a colour", it, style = MaterialTheme.typography.bodySmall) },
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
            onRename = {},
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
