package com.ivandsky.puzzlebox.create

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import org.koin.compose.koinInject

/**
 * The Create root's entry point over the activity-scoped [viewModel] (CreateEntries); [onOpen]
 * opens a draft's Studio, new ones included once the view model has stored them;
 * [onOpenPublished] opens a published puzzle's page by its share code. The Published tab shows
 * only while [ReleaseFlag.FriendSharing] is on (ADR-0022).
 */
@Composable
internal fun CreateRoute(
    viewModel: CreateViewModel,
    onOpen: (draftId: String) -> Unit,
    onOpenPublished: (code: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val flags by koinInject<ReleaseFlags>().enabled.collectAsStateWithLifecycle()
    val sharing = ReleaseFlag.FriendSharing in flags
    val open by rememberUpdatedState(onOpen)
    LaunchedEffect(viewModel) { viewModel.created.collect { open(it) } }
    CreateScreen(
        state = if (sharing) state else state.copy(tab = CreateUiState.Tab.Drafts, published = emptyList()),
        onNew = viewModel::newPuzzle,
        onOpen = onOpen,
        onQuery = viewModel::onQuery,
        onFilter = viewModel::onFilter,
        onTab = viewModel::onTab,
        onOpenPublished = onOpenPublished,
        onDuplicate = viewModel::duplicate,
        onDelete = viewModel::delete,
        onUndoDelete = viewModel::undoDelete,
        showPublished = sharing,
        modifier = modifier.testTag("route.create"),
    )
}
