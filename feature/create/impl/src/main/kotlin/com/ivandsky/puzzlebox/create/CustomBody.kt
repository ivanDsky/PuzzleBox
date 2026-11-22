package com.ivandsky.puzzlebox.create

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.create.importtext.ImportSheet
import com.ivandsky.puzzlebox.create.importtext.ImportSheetActions
import com.ivandsky.puzzlebox.create.navigation.rememberCreateViewModel
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * The Custom list of Games (spec N9, N10): the author's drafts and published puzzles, as one list on [listState] that fills
 * the space it is given. Top to bottom:
 *
 * 1. **New puzzle** opens the New puzzle sheet; the draft it starts is reported to [onOpenDraft]. Inside a
 *    [NewPuzzleHost] the row is left out: the host's button opens the sheet, and the list ends clear of that button.
 * 2. **Continue**: the drafts. A tap reports the draft's id to [onOpenDraft]; ⋮ or a long press offers Continue,
 *    Duplicate and Delete, and a delete can be undone from the snackbar at the bottom.
 * 3. **Published by you**: a tap reports the puzzle's share code to [onOpenPublished].
 *
 * With no drafts and nothing published, the first-time empty state shows in the list's place (pick a game to
 * start a draft, or import one).
 *
 * It is a root of the one activity-scoped [CreateViewModel], so drafts, the Undo window and an import carry over
 * to and from the Studio. While it shows, text another app shared is imported and opened through [onOpenDraft],
 * and the published rows' solve counts are fetched once.
 *
 * Motion: rows **Fade in** / **Fade out** and slide to new places; the first cloud restore's fill-in is a
 * **Fade through**. Reduced motion drops the row motion and shortens the fade.
 */
@Composable
fun CustomBody(
    listState: LazyListState,
    onOpenDraft: (String) -> Unit,
    onOpenPublished: (String) -> Unit,
) {
    val viewModel = rememberCreateViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val importSheet by viewModel.importText.ui.collectAsStateWithLifecycle()
    val hostClearance = LocalNewPuzzleClearance.current
    CreateEffects(viewModel, onOpen = if (hostClearance == null) onOpenDraft else null)
    LaunchedEffect(viewModel) { viewModel.syncSolveCounts() }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var actionsId by rememberSaveable { mutableStateOf<String?>(null) }
    CustomBodyContent(
        state = state,
        listState = listState,
        onNewPuzzle = if (hostClearance == null) ({ sheetOpen = true }) else null,
        fabClearance = hostClearance ?: 0.dp,
        onStart = viewModel::newPuzzle,
        onImport = viewModel.importText::open,
        onOpenDraft = onOpenDraft,
        onDraftActions = { actionsId = it },
        onOpenPublished = onOpenPublished,
        onUndoDelete = viewModel::undoDelete,
    )
    if (hostClearance == null) {
        ImportSheet(ui = importSheet, actions = remember(viewModel) { ImportSheetActions.of(viewModel.importText) })
    }
    if (sheetOpen && hostClearance == null) {
        NewPuzzleSheet(
            games = state.games,
            onPick = { gameId, variant ->
                sheetOpen = false
                viewModel.newPuzzle(gameId, variant)
            },
            onDismiss = { sheetOpen = false },
            onImport = {
                sheetOpen = false
                viewModel.importText.open()
            },
        )
    }
    state.drafts.firstOrNull { it.id == actionsId }?.let { row ->
        DraftActionsSheet(
            row = row,
            onContinue = { actionsId = null; onOpenDraft(row.id) },
            onDuplicate = { actionsId = null; viewModel.duplicate(row.id) },
            onDelete = { actionsId = null; viewModel.delete(row.id) },
            onDismiss = { actionsId = null },
        )
    }
}

/**
 * [CustomBody]'s layout over a given [state], with no view model and no sheets: the list (or, with nothing at all
 * to list, the first-time screen) under the delete-Undo snackbar. [onNewPuzzle] is the New puzzle button, left out
 * when null; [fabClearance] is the room a button over the bottom end takes, which the list and the snackbar keep free.
 * [onStart] and [onImport] are the first-time screen's game rows and its "Import from text".
 */
@Composable
internal fun CustomBodyContent(
    state: CreateUiState,
    listState: LazyListState,
    onNewPuzzle: (() -> Unit)?,
    onStart: (GameId, VariantId) -> Unit,
    onImport: () -> Unit,
    onOpenDraft: (String) -> Unit,
    onDraftActions: (String) -> Unit,
    onOpenPublished: (String) -> Unit,
    onUndoDelete: () -> Unit,
    fabClearance: Dp = 0.dp,
) {
    val reducedMotion = rememberReducedMotion()
    Box(Modifier.fillMaxSize()) {
        // Keyed on the restoring card alone, only the first cloud restore's fill-in animates.
        Crossfade(
            targetState = state.restoringScreen(),
            animationSpec = tween(createFadeMillis(reducedMotion)),
            label = "customRestore",
        ) { restoring ->
            when {
                restoring -> CreateEmptyState(games = state.games, onStart = onStart, onImport = onImport, restoring = true)
                state.loading -> Unit
                state.isEmpty -> CreateEmptyState(games = state.games, onStart = onStart, onImport = onImport)
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().testTag("custom.list"),
                    contentPadding = PaddingValues(top = Spacing.sm, bottom = maxOf(Spacing.xl, fabClearance)),
                ) {
                    if (onNewPuzzle != null) {
                        item(key = "custom.new") {
                            PrimaryButton(
                                text = "New puzzle",
                                onClick = onNewPuzzle,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).testTag("custom.new"),
                            )
                        }
                    }
                    if (state.drafts.isNotEmpty()) {
                        item(key = "custom.continue") {
                            SectionHead("Continue", state.drafts.size, Modifier.testTag("custom.continue"))
                        }
                        draftRows(state.drafts, reducedMotion, onOpenDraft, onDraftActions)
                    }
                    if (state.published.isNotEmpty()) {
                        item(key = "custom.published") {
                            SectionHead("Published by you", state.published.size, Modifier.testTag("custom.published"))
                        }
                        publishedRows(state.published, reducedMotion, onOpenPublished)
                    }
                }
            }
        }
        DeleteUndoSnackbar(
            rememberDeleteUndo(state.deleted, onUndoDelete),
            Modifier.align(Alignment.BottomCenter).padding(bottom = fabClearance),
        )
    }
}

/** A section's [title] with how many rows it holds. */
@Composable
private fun SectionHead(title: String, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg, bottom = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
    }
}

/** Two drafts over a friends stand-in and one published puzzle. */
@PuzzleBoxPreviews
@Composable
private fun CustomBodyContentPreview() {
    val drafts = PreviewGames.take(2).mapIndexed { index, game ->
        CreateUiState.DraftRow(
            id = "d$index",
            gameId = game.id,
            brand = game.brand,
            title = "Untitled ${index + 1}",
            meta = "${game.name} · ${game.defaultSize.label}",
            edited = "edited ${index + 1} h ago",
            verdict = Verdict.Undetermined,
        )
    }
    val published = PreviewGames.first().let { game ->
        CreateUiState.PublishedRow(
            code = checkNotNull(ShareCode.parse("K7Q2MX")),
            gameId = game.id,
            brand = game.brand,
            title = "Corner trap",
            meta = "${game.name} · your time 1:42",
            published = "published 2 d ago",
        )
    }
    PuzzleBoxTheme {
        Surface {
            CustomBodyContent(
                state = CreateUiState(loading = false, drafts = drafts, draftCount = drafts.size, games = PreviewGames, published = listOf(published)),
                listState = rememberLazyListState(),
                onNewPuzzle = {},
                onStart = { _, _ -> },
                onImport = {},
                onOpenDraft = {},
                onDraftActions = {},
                onOpenPublished = {},
                onUndoDelete = {},
            )
        }
    }
}
