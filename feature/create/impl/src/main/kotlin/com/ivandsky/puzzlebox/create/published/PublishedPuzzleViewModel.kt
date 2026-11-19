package com.ivandsky.puzzlebox.create.published

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.create.api.PublishedPuzzleKey
import com.ivandsky.puzzlebox.create.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.create.domain.UpdatePublishState
import com.ivandsky.puzzlebox.create.editor.LoadDraft
import com.ivandsky.puzzlebox.create.editor.ShareTicket
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.data.repository.PublishedPuzzleRepository
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorController
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import com.ivandsky.puzzlebox.gameui.hidden.GivensBoard
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.CoroutineScope
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
 * kept draft whose reserved code it is. The page draws the draft's plain board (read-only, no verdict), under the
 * share ticket from the row's title and author time, the draft's author name and the game's name and hue.
 * [duplicate] stores an editable copy without the test solve or the code, so it publishes under a new one.
 * [unpublish] takes the code down behind its confirm; the row's unpublished mark flows into the ticket as it lands.
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
                showPlainBoard(ready)
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
                // A row restored from a legacy doc has no author time; the ticket then shows none.
                authorTimeMs = row.authorTimeMs.takeIf { it > 0 },
                game = games.displayName(row.gameId),
                accent = plays.brandOf(row.gameId).accent,
                unpublished = row.unpublished,
            ),
            ui = ui,
            controller = controller,
            draftId = stored.id,
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

    /**
     * The plain board of [ready]'s kept draft: the draft's plain puzzle
     * ([EditorDraft.toPuzzle], without author aids). The game's export needs the solution, so the one solve within
     * [BUDGET_MS] runs for that alone, and its verdict is dropped. The solution is dropped again by
     * [GameReplayBoard.givens][com.ivandsky.puzzlebox.gameui.replay.GameReplayBoard.givens]
     * before it draws anything. A game that cannot export or draw the board leaves the page without one.
     */
    private suspend fun showPlainBoard(ready: State.Ready) {
        val board = ready.controller.draft.value
        val givens = withContext(dispatchers.default) { plainPuzzle(board)?.let { plays[board.gameId]?.replayBoard?.givens(it) } }
        mutableState.update { (it as? State.Ready)?.copy(givens = givens, givensSettled = true) ?: it }
    }

    private fun CoroutineScope.plainPuzzle(board: EditorDraft<*>): Puzzle? {
        val verdict = board.analyze(Budget.within(BUDGET_MS, System::nanoTime) { !isActive })
        return (verdict as? Verdict.Unique)?.let { board.toPuzzle(it.solution) }
    }

    /** The page: loading, a code with no row or kept draft this build can open, or [Ready]. */
    internal sealed interface State {
        data object Loading : State
        data object Missing : State

        /**
         * The [ticket] and the kept draft [draftId]'s [ui] and [controller] (the game's own publish fields). The page
         * draws the draft's plain [givens] as its board: null until built, and for good once [givensSettled] when this
         * game cannot draw one.
         */
        data class Ready(
            val ticket: ShareTicket,
            val ui: GameEditorUi,
            val controller: GameEditorController,
            val draftId: String,
            val givens: GivensBoard? = null,
            val givensSettled: Boolean = false,
        ) : State
    }

    private companion object {
        const val BUDGET_MS = 1_500L
    }
}
