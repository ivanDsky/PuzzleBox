package com.ivandsky.puzzlebox.create

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.create.importtext.ImportSheetActions

/**
 * The Create root's entry point over the activity-scoped [viewModel] (CreateEntries); [onOpen]
 * opens a draft's Studio, new ones included once the view model has stored them;
 * [onOpenPublished] opens a published puzzle's page by its share code. While it shows it also
 * imports the text another app shared ([CreateViewModel.importSharedText]).
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
    LaunchedEffect(viewModel) { viewModel.importSharedText() }
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
        importActions = remember(viewModel) { ImportSheetActions.of(viewModel.importText) },
    )
}
