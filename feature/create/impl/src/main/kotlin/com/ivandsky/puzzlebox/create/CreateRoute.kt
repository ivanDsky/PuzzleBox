package com.ivandsky.puzzlebox.create

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The Create root's entry point over the activity-scoped [viewModel] (CreateEntries); [onOpen]
 * opens a draft's Studio, new ones included.
 */
@Composable
internal fun CreateRoute(
    viewModel: CreateViewModel,
    onOpen: (draftId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CreateScreen(
        state = state,
        onNew = { gameId, variant -> viewModel.newPuzzle(gameId, variant, onCreated = onOpen) },
        onOpen = onOpen,
        onQuery = viewModel::onQuery,
        onFilter = viewModel::onFilter,
        onDuplicate = viewModel::duplicate,
        onDelete = viewModel::delete,
        onUndoDelete = viewModel::undoDelete,
        modifier = modifier.testTag("route.create"),
    )
}
