package com.ivandsky.puzzlebox.create.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Studio's thin composition root: opens the stored draft named by [key], holds its
 * [EditorDraft], hands every edit to its holders and autosaves it with its undo history
 * ([saving]; stored at once on [flush], before [duplicate] and on leaving the Studio, dropped
 * by [delete]). Each concern moves into its own holder, wired here: VerdictPipeline (13),
 * EditHistory (14/15), DraftMeta (16), DraftAutosave (17), VerdictCardState (24),
 * TestSolveSession (27).
 */
class EditorViewModel(
    private val key: EditorKey,
    private val drafts: DraftRepository,
    private val games: GameRegistry,
    private val editors: GameEditorRegistry,
    copyDraft: CreateDraftCopy,
    dispatchers: DispatcherProvider,
    private val clock: () -> Instant,
) : ViewModel() {

    private val mutableState = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val state: StateFlow<EditorUiState> = mutableState.asStateFlow()

    private val verdicts = VerdictPipeline(viewModelScope, dispatchers)
    private val history = EditHistory()

    /** The verdict card; Check again resubmits the board as it stands. */
    val verdictCard = VerdictCardState { (state.value as? EditorUiState.Ready)?.controller?.draft?.value?.let(verdicts::recheck) }

    /** The draft's title, rename, duplicate and delete. */
    val meta = DraftMeta(key.draftId, copyDraft, viewModelScope) { title -> lastStored?.let { write(it.copy(title = title)) } }

    /** Undo and Redo are enabled only when the open draft has a step to move. */
    val canUndo: StateFlow<Boolean> = history.canUndo
    val canRedo: StateFlow<Boolean> = history.canRedo

    /** The stored row as last written; null until the draft has loaded. */
    private var lastStored: Draft? = null

    /**
     * The saves' own scope: it outlives [viewModelScope], which is cancelled before [onCleared],
     * so leaving the Studio still stores the last edit. It ends once that final save is stored.
     */
    private val saves = CoroutineScope(SupervisorJob() + dispatchers.main)
    private val autosave = DraftAutosave(saves, drafts::upsert)

    /** "Saving…" while an edit waits to be stored, "✓ Saved" once it is. */
    val saving: StateFlow<Boolean> = autosave.saving

    init {
        viewModelScope.launch { mutableState.value = load() }
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
        meta.open(stored.title)
        history.open(draft)
        // The verdict lands after the debounce; it is only published once this state is Ready.
        verdicts.submit(draft)
        return EditorUiState.Ready(
            subtitle = subtitleOf(draft, ui),
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

    /** A blank [variant] board as one undoable step (A5); picking the open size is not a step. */
    fun resize(variant: VariantId) {
        history.resize(variant)?.let(::adopt)
    }

    /** A blank board of the open size as one undoable step (A5). */
    fun clear() {
        history.clear()?.let(::adopt)
    }

    /** App stop (A5): the pending edit is stored now, not at the window's end. */
    fun flush() {
        saves.launch { autosave.flush() }
    }

    /** Duplicate copies the stored row, so the pending edit is stored first and the copy has it. */
    fun duplicate(onCopied: (draftId: String) -> Unit) {
        saves.launch(start = CoroutineStart.UNDISPATCHED) {
            autosave.flush()
            meta.duplicate(onCopied)
        }
    }

    /** Delete drops the pending edit first, so no late save brings the deleted draft back. */
    fun delete(onDelete: (draftId: String) -> Unit) {
        saves.launch(start = CoroutineStart.UNDISPATCHED) {
            autosave.discard()
            meta.delete(onDelete)
        }
    }

    /** Back, a tab's stack dropping the Studio, Duplicate's replace: the last edit is stored, then the saves end. */
    override fun onCleared() {
        saves.launch { autosave.flush() }.invokeOnCompletion { saves.cancel() }
    }

    /** Undo, redo, resize and clear replace the board; only these can change its size, so the subtitle follows here. */
    private fun adopt(draft: EditorDraft<*>) {
        (mutableState.value as? EditorUiState.Ready)?.let { ready ->
            ready.controller.adopt(draft)
            mutableState.value = ready.copy(subtitle = subtitleOf(draft, ready.editor))
        }
        changed(draft)
    }

    private fun subtitleOf(draft: EditorDraft<*>, ui: GameEditorUi): String =
        "${games.displayName(draft.gameId)} ${ui.sizeLabel(draft.variant)}"

    private fun onEdit(draft: EditorDraft<*>) {
        history.record(draft)
        changed(draft)
    }

    private fun changed(draft: EditorDraft<*>) {
        verdictCard.dismiss()
        verdicts.submit(draft)
        val base = lastStored ?: return
        write(base.copy(variant = draft.variant, blob = draft.encode()))
    }

    /** Stamps [next] and hands it to the autosave. */
    private fun write(next: Draft) {
        val stamped = next.copy(updatedAt = clock())
        lastStored = stamped
        autosave.offer(stamped)
    }
}
