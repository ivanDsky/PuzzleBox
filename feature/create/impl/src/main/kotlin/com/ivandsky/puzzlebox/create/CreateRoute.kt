package com.ivandsky.puzzlebox.create

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The Create root's entry point over the activity-scoped [viewModel] (CreateEntries); [onOpen]
 * opens a draft's Studio, new ones included once the view model has stored them;
 * [onOpenPublished] opens a published puzzle's page by its share code.
 */
@Composable
internal fun CreateRoute(
    viewModel: CreateViewModel,
    onOpen: (draftId: String) -> Unit,
    onOpenPublished: (code: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val importEnabled by viewModel.importEnabled.collectAsStateWithLifecycle()
    val importSheet by viewModel.importText.ui.collectAsStateWithLifecycle()
    val open by rememberUpdatedState(onOpen)
    LaunchedEffect(viewModel) { viewModel.created.collect { open(it) } }
    CreateScreen(
        state = state,
        onNew = viewModel::newPuzzle,
        onOpen = onOpen,
        onQuery = viewModel::onQuery,
        onFilter = viewModel::onFilter,
        onTab = viewModel::onTab,
        onOpenPublished = onOpenPublished,
        onDuplicate = viewModel::duplicate,
        onDelete = viewModel::delete,
        onUndoDelete = viewModel::undoDelete,
        modifier = modifier.testTag("route.create"),
        importEnabled = importEnabled,
        importSheet = importSheet,
        onImportOpen = viewModel.importText::open,
        onImportEdit = viewModel.importText::edit,
        onImportSubmit = viewModel.importText::submit,
        onImportDismiss = viewModel.importText::dismiss,
        onImportClipboard = viewModel.importText::offerClipboard,
        onImportPaste = viewModel.importText::pasteClipboard,
        onImportClipboardDismiss = viewModel.importText::dismissClipboard,
    )
}
