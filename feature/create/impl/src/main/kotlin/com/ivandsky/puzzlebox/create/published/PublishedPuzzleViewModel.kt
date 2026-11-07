package com.ivandsky.puzzlebox.create.published

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.create.api.PublishedPuzzleKey
import com.ivandsky.puzzlebox.create.editor.LoadDraft
import com.ivandsky.puzzlebox.create.editor.ShareTicket
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.data.repository.PublishedPuzzleRepository
import com.ivandsky.puzzlebox.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.domain.UpdatePublishState
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorController
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The published page of [key]'s code: the stored [PublishedPuzzleRepository] row joined with the
 * kept draft whose reserved code it is. That draft's board shows read-only (its controller puts
 * the board back over any edit), under the share ticket from the row's title and author time,
 * the draft's author name and the game's name and hue. [duplicate] stores an editable copy
 * without the test solve or the code, so it publishes under a new one. [unpublish] takes the
 * code down behind its confirm; the row's unpublished mark flows into the ticket as it lands.
 */
class PublishedPuzzleViewModel(
    private val key: PublishedPuzzleKey,
    private val published: PublishedPuzzleRepository,
    private val drafts: DraftRepository,
    private val games: GameRegistry,
    editors: GameEditorRegistry,
    private val plays: GamePlayRegistry,
    private val copyDraft: CreateDraftCopy,
    private val dispatchers: DispatcherProvider,
    updatePublishState: UpdatePublishState,
) : ViewModel() {

    private val loadDraft = LoadDraft(drafts, games, editors)
    private val mutableState = MutableStateFlow<State>(State.Loading)
    internal val state: StateFlow<State> = mutableState.asStateFlow()

    /** Unpublish and its confirm dialog. */
    internal val unpublish = UnpublishSession(updatePublishState, viewModelScope)

    init {
        viewModelScope.launch {
            val ready = load()
            mutableState.value = ready ?: State.Missing
            if (ready != null) {
                follow(ready.ticket.code)
                analyze(ready)
            }
        }
    }

    /** The page's Unpublish, once confirmed in its dialog. */
    fun confirmUnpublish() {
        val ready = state.value as? State.Ready ?: return
        unpublish.confirm(ready.ticket.code)
    }

    /** Stores an editable copy of the published board and reports its id through [onCopied]. */
    fun duplicate(onCopied: (draftId: String) -> Unit) {
        val ready = state.value as? State.Ready ?: return
        viewModelScope.launch { copyDraft(ready.draftId)?.let { onCopied(it.id) } }
    }

    private suspend fun load(): State.Ready? {
        val code = ShareCode.parse(key.code) ?: return null
        val row = published.observe(code).first() ?: return null
        val kept = drafts.observeAll().first().firstOrNull { it.reservedCode == code.value } ?: return null
        val (stored, draft, ui) = loadDraft(kept.id) ?: return null
        lateinit var controller: GameEditorController
        controller = ui.createController(draft) { controller.adopt(draft) }
        return State.Ready(
            ticket = ShareTicket(
                code = code,
                title = row.title,
                author = stored.authorName.trim(),
                authorTimeMs = row.authorTimeMs,
                game = games.displayName(row.gameId),
                accent = plays.brandOf(row.gameId).accent,
                unpublished = row.unpublished,
            ),
            ui = ui,
            controller = controller,
            draftId = stored.id,
            verdict = Verdict.Checking,
        )
    }

    /** Keeps the ticket's unpublished mark on the stored row's, from the load on. */
    private fun follow(code: ShareCode) {
        viewModelScope.launch {
            published.observe(code).collect { row ->
                val unpublished = row?.unpublished ?: return@collect
                mutableState.update { (it as? State.Ready)?.copy(ticket = it.ticket.copy(unpublished = unpublished)) ?: it }
            }
        }
    }

    private suspend fun analyze(ready: State.Ready) {
        val board = ready.controller.draft.value
        val verdict = withContext(dispatchers.default) { board.analyze(Budget.within(BUDGET_MS, System::nanoTime) { !isActive }) }
        mutableState.update { (it as? State.Ready)?.copy(verdict = verdict) ?: it }
    }

    /** The page: loading, a code with no row or kept draft this build can open, or [Ready]. */
    internal sealed interface State {
        data object Loading : State
        data object Missing : State

        /** The [ticket], the kept draft [draftId]'s read-only board ([ui], [controller]) and its [verdict]. */
        data class Ready(
            val ticket: ShareTicket,
            val ui: GameEditorUi,
            val controller: GameEditorController,
            val draftId: String,
            val verdict: Verdict,
        ) : State
    }

    private companion object {
        const val BUDGET_MS = 1_500L
    }
}
