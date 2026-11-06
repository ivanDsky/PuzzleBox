package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.PlayLayout
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** The Studio's DI-bound entry point: one [EditorViewModel] per [key]; the game's editor UI draws the board. */
@Composable
internal fun EditorRoute(
    key: EditorKey,
    layout: PlayLayout,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: EditorViewModel = koinViewModel(key = key.toString(), parameters = { parametersOf(key) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
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
            title = ready.title,
            subtitle = ready.subtitle,
            verdict = ready.verdict,
            layout = layout,
            onBack = onBack,
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
        )
    }
}
