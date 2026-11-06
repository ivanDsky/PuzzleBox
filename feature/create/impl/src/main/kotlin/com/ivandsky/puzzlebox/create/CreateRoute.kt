package com.ivandsky.puzzlebox.create

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

/** The Create root's DI-bound entry point; [onOpen] opens a draft's Studio, new ones included. */
@Composable
internal fun CreateRoute(
    onOpen: (draftId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CreateScreen(
        state = state,
        onNew = { gameId, variant -> viewModel.newPuzzle(gameId, variant, onCreated = onOpen) },
        onOpen = onOpen,
        onQuery = viewModel::onQuery,
        onFilter = viewModel::onFilter,
        modifier = modifier.testTag("route.create"),
    )
}
