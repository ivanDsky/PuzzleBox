package com.ivandsky.puzzlebox.create.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * The Studio's thin composition root: opens the stored draft named by [key], holds its
 * [EditorDraft], hands every edit to its holders and writes it back. Each concern moves into
 * its own holder, wired here: VerdictPipeline (13), EditHistory (14/15), DraftMeta (16),
 * DraftAutosave (17), VerdictCardState (24), TestSolveSession (27).
 */
class EditorViewModel(
    private val key: EditorKey,
    private val drafts: DraftRepository,
    private val games: GameRegistry,
    private val editors: GameEditorRegistry,
    dispatchers: DispatcherProvider,
    private val clock: () -> Instant,
) : ViewModel() {

    private val mutableState = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val state: StateFlow<EditorUiState> = mutableState.asStateFlow()

    private val verdicts = VerdictPipeline(viewModelScope, dispatchers)
    private val history = EditHistory()

    /** Undo and Redo are enabled only when the open draft has a step to move. */
    val canUndo: StateFlow<Boolean> = history.canUndo
    val canRedo: StateFlow<Boolean> = history.canRedo

    /** The stored row as last written; null until the draft has loaded. */
    private var lastStored: Draft? = null

    /** The next row to write; null until the first edit. */
    private val pending = MutableStateFlow<Draft?>(null)

    init {
        viewModelScope.launch { mutableState.value = load() }
        // One writer, newest value wins: edits faster than the store are conflated, never reordered.
        viewModelScope.launch { pending.filterNotNull().collect { drafts.upsert(it) } }
        viewModelScope.launch {
            verdicts.verdict.collect { verdict ->
                val ready = mutableState.value as? EditorUiState.Ready ?: return@collect
                mutableState.value = ready.copy(verdict = verdict)
            }
        }
    }

    private suspend fun load(): EditorUiState {
        val stored = drafts.get(key.draftId) ?: return EditorUiState.Missing
        val editor = games[stored.gameId]?.editor ?: return EditorUiState.Missing
        val ui = editors[stored.gameId] ?: return EditorUiState.Missing
        val draft = EditorDraft.open(editor, stored.blob).getOrNull() ?: return EditorUiState.Missing
        lastStored = stored
        history.open(draft)
        // The verdict lands after the debounce; it is only published once this state is Ready.
        verdicts.submit(draft)
        return EditorUiState.Ready(
            title = stored.title,
            subtitle = "${games.displayName(stored.gameId)} ${ui.sizeLabel(stored.variant)}",
            verdict = verdicts.verdict.value,
            editor = ui,
            controller = ui.createController(draft, ::onEdit),
        )
    }

    /** One step back: the board adopts it, then it is checked and stored like any edit. */
    fun undo() {
        history.undo()?.let(::adopt)
    }

    /** The undone step again, handled like [undo]. */
    fun redo() {
        history.redo()?.let(::adopt)
    }

    private fun adopt(draft: EditorDraft<*>) {
        (mutableState.value as? EditorUiState.Ready)?.controller?.adopt(draft)
        changed(draft)
    }

    private fun onEdit(draft: EditorDraft<*>) {
        history.record(draft)
        changed(draft)
    }

    private fun changed(draft: EditorDraft<*>) {
        verdicts.submit(draft)
        val base = lastStored ?: return
        val next = base.copy(blob = draft.encode(), updatedAt = clock())
        lastStored = next
        pending.value = next
    }
}
