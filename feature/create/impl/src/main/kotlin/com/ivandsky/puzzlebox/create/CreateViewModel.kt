package com.ivandsky.puzzlebox.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.domain.CreateDraft
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Create root: lists the stored drafts and starts new ones. [newPuzzle] opens the first
 * registered game that has both an editor and an editor UI, at its first variant.
 */
class CreateViewModel(
    private val games: GameRegistry,
    private val editors: GameEditorRegistry,
    drafts: DraftRepository,
    private val createDraft: CreateDraft,
) : ViewModel() {

    val uiState: StateFlow<CreateUiState> = drafts.observeAll()
        .map { stored ->
            CreateUiState(
                stored.map { draft ->
                    val size = editors[draft.gameId]?.sizeLabel(draft.variant) ?: draft.variant.value
                    CreateUiState.DraftRow(
                        id = draft.id,
                        title = draft.title,
                        meta = "${games.displayName(draft.gameId)} · $size",
                    )
                },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CreateUiState())

    /** Stores a blank draft and reports its id; a no-op while no game has an editor. */
    fun newPuzzle(onCreated: (draftId: String) -> Unit) {
        val plugin = games.all.firstOrNull { it.editor != null && it.definition.id in editors.ids } ?: return
        val editor = checkNotNull(plugin.editor)
        viewModelScope.launch {
            val draft = createDraft(plugin.definition.id, editor.variants.first())
            onCreated(draft.id)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
