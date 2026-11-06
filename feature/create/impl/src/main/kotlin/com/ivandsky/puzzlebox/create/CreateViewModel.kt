package com.ivandsky.puzzlebox.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.domain.CreateDraft
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Create root: lists the stored drafts and starts new ones. The New puzzle sheet offers
 * every game with an editor UI ([GameEditorRegistry.ids], registration order) whose plugin
 * carries the editor, each at exactly its editor's variants (spec A1, A5).
 */
class CreateViewModel(
    private val games: GameRegistry,
    private val editors: GameEditorRegistry,
    drafts: DraftRepository,
    private val createDraft: CreateDraft,
) : ViewModel() {

    private val newGames: List<CreateUiState.NewGame> = editors.ids.mapNotNull { id ->
        val editor = games[id]?.editor ?: return@mapNotNull null
        val ui = editors[id] ?: return@mapNotNull null
        CreateUiState.NewGame(
            id = id,
            name = games.displayName(id),
            sizes = editor.variants.map { CreateUiState.Size(it, ui.sizeLabel(it)) },
        )
    }

    val uiState: StateFlow<CreateUiState> = drafts.observeAll()
        .map { stored ->
            CreateUiState(
                drafts = stored.map { draft ->
                    val size = editors[draft.gameId]?.sizeLabel(draft.variant) ?: draft.variant.value
                    CreateUiState.DraftRow(
                        id = draft.id,
                        title = draft.title,
                        meta = "${games.displayName(draft.gameId)} · $size",
                    )
                },
                games = newGames,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CreateUiState(games = newGames))

    /** Stores a blank [variant] draft of [gameId] (a choice the sheet offered) and reports its id. */
    fun newPuzzle(gameId: GameId, variant: VariantId, onCreated: (draftId: String) -> Unit) {
        viewModelScope.launch {
            onCreated(createDraft(gameId, variant).id)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
