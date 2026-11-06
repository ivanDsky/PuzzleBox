package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.PlayLayout
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The Studio's DI-bound entry point: one [EditorViewModel] per [key]; the game's editor UI
 * draws the board. Edits autosave, and the app's stop (ON_STOP) stores the pending one at once
 * (A5). Duplicate stores the pending edit, then reports the copy's id to [onOpen]; Delete stores
 * it too, then reports the draft's id to [onDelete], which leaves the Studio and deletes it with Undo.
 * The verdict card hosts the game's fix list over the draft's board blocks (`publishBlocks`);
 * a fix's Select closes the card so the selection shows.
 */
@Composable
internal fun EditorRoute(
    key: EditorKey,
    layout: PlayLayout,
    onBack: () -> Unit,
    onOpen: (draftId: String) -> Unit,
    onDelete: (draftId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: EditorViewModel = koinViewModel(key = key.toString(), parameters = { parametersOf(key) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val title by viewModel.meta.title.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flush() }
    val routeModifier = modifier.testTag("route.editor")
    when (val ready = state) {
        EditorUiState.Loading -> Box(routeModifier.fillMaxSize())
        EditorUiState.Missing -> Box(routeModifier.fillMaxSize().padding(Spacing.lg), contentAlignment = Alignment.Center) {
            Text(
                text = "This draft can't be opened.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is EditorUiState.Ready -> StudioScreen(
            title = title,
            subtitle = ready.subtitle,
            saving = saving,
            verdict = ready.verdict,
            verdictCard = viewModel.verdictCard,
            layout = layout,
            onBack = onBack,
            onRename = viewModel.meta::rename,
            onDuplicate = { viewModel.duplicate(onOpen) },
            onDelete = { viewModel.delete(onDelete) },
            board = { boardModifier -> ready.editor.Board(ready.controller, ready.verdict, boardModifier) },
            toolStrip = { stripModifier ->
                // The board's controller adopts every resize and undo, so its draft names the open size.
                val draft by ready.controller.draft.collectAsStateWithLifecycle()
                ToolStrip(
                    canUndo = canUndo,
                    canRedo = canRedo,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    sizes = draft.editor.variants,
                    size = draft.variant,
                    sizeLabel = ready.editor::sizeLabel,
                    onResize = viewModel::resize,
                    onClear = viewModel::clear,
                    hint = { hintModifier -> ready.editor.ToolHint(ready.controller, hintModifier) },
                    modifier = stripModifier,
                )
            },
            modifier = routeModifier,
            issues = { issuesModifier ->
                val draft by ready.controller.draft.collectAsStateWithLifecycle()
                val blocks = remember(draft, ready.verdict) { draft.publishBlocks(ready.verdict) }
                ready.editor.Issues(ready.controller, ready.verdict, blocks, viewModel.verdictCard::dismiss, issuesModifier)
            },
        )
    }
}
