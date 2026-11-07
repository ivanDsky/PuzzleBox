package com.ivandsky.puzzlebox.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.PublishedPuzzle
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.data.repository.PublishedPuzzleRepository
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.domain.CreateDraft
import com.ivandsky.puzzlebox.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.model.GameId
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Create root: lists the stored drafts and starts new ones. The New puzzle sheet and the
 * empty state offer every game with an editor UI ([GameEditorRegistry.ids], registration
 * order) whose plugin carries the editor, each at exactly its editor's variants (spec A1, A5)
 * with the middle one as its default size, under its [GamePlayRegistry] brand.
 *
 * Each row's verdict chip (spec A7 on the root) is `analyze(draft, Budget.within(1_500…))` on
 * [DispatcherProvider.default], cached by (id, updatedAt): a draft is analysed again only after
 * an edit. The search and game filter apply only past [SEARCH_THRESHOLD] drafts.
 *
 * A row's actions (01 handoff "Draft actions sheet"): [duplicate] stores a copy without the
 * test solve; [delete] removes the draft at once and offers [undoDelete] for [UNDO_WINDOW_MS],
 * which stores the very same draft again, so it returns to its place in the list.
 *
 * The [CreateUiState.Tab.Published] tab lists [published]'s rows, newest first; a draft kept
 * under a published code is that puzzle's board, so it leaves the Drafts tab, its counts, its
 * search and its verdict chips.
 */
class CreateViewModel(
    private val games: GameRegistry,
    private val editors: GameEditorRegistry,
    private val plays: GamePlayRegistry,
    private val drafts: DraftRepository,
    private val published: PublishedPuzzleRepository,
    private val createDraft: CreateDraft,
    private val copyDraft: CreateDraftCopy,
    private val dispatchers: DispatcherProvider,
    private val clock: () -> Instant,
    private val nanoTime: () -> Long = System::nanoTime,
) : ViewModel() {

    private val newGames: List<CreateUiState.NewGame> = editors.ids.mapNotNull { id ->
        val editor = games[id]?.editor ?: return@mapNotNull null
        val ui = editors[id] ?: return@mapNotNull null
        val sizes = editor.variants.map { CreateUiState.Size(it, ui.sizeLabel(it)) }
        if (sizes.isEmpty()) return@mapNotNull null
        CreateUiState.NewGame(
            id = id,
            name = games.displayName(id),
            brand = plays.brandOf(id),
            rule = gameRuleLine(id),
            defaultSize = sizes[sizes.size / 2],
            sizes = sizes,
        )
    }

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow<GameId?>(null)
    private val tab = MutableStateFlow(CreateUiState.Tab.Drafts)

    /** Settled chips by (id, updatedAt); a null verdict is a draft this build cannot open. */
    private val verdicts = MutableStateFlow<Map<DraftStamp, Verdict?>>(emptyMap())

    /** The last deleted draft while its Undo is offered. */
    private val deleted = MutableStateFlow<Draft?>(null)
    private var undoWindow: Job? = null
    private var creating: Job? = null

    /** Stamps already analysed or in flight; touched on the main thread only. */
    private val requested = mutableSetOf<DraftStamp>()

    val uiState: StateFlow<CreateUiState> =
        combine(listed(), verdicts, combine(query, filter, tab, ::View), deleted, ::stateOf)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CreateUiState(games = newGames))

    fun onQuery(text: String) {
        query.value = text
    }

    /** Shows [shown]'s list; a switch starts it unsearched and unfiltered. */
    fun onTab(shown: CreateUiState.Tab) {
        if (tab.value == shown) return
        tab.value = shown
        query.value = ""
        filter.value = null
    }

    /** Shows only [game]'s drafts; null shows every game. */
    fun onFilter(game: GameId?) {
        filter.value = game
    }

    private val createdIds = Channel<String>(Channel.BUFFERED)

    /** The id of each draft [newPuzzle] stored, once each, for the route to open. */
    val created: Flow<String> = createdIds.receiveAsFlow()

    /**
     * Stores a blank [variant] draft of [gameId] (a choice the sheet offered) and sends its id on
     * [created]. A pick while the previous draft is still being stored is dropped, so a double
     * tap makes one draft.
     */
    fun newPuzzle(gameId: GameId, variant: VariantId) {
        if (creating?.isActive == true) return
        creating = viewModelScope.launch {
            createdIds.send(createDraft(gameId, variant).id)
        }
    }

    /** Stores a copy of [draftId]'s board titled "{title} 2" (the next free number). */
    fun duplicate(draftId: String) {
        viewModelScope.launch { copyDraft(draftId) }
    }

    /**
     * Removes [draftId] now; [undoDelete] puts it back until [UNDO_WINDOW_MS] pass. An id with no
     * stored draft (a repeat tap) leaves the open Undo window and its timer as they are.
     */
    fun delete(draftId: String) {
        viewModelScope.launch {
            val draft = drafts.get(draftId) ?: return@launch
            undoWindow?.cancel()
            drafts.delete(draftId)
            deleted.value = draft
            undoWindow = viewModelScope.launch {
                delay(UNDO_WINDOW_MS)
                deleted.value = null
            }
        }
    }

    /** Restores the last deleted draft, byte for byte, while its Undo is still offered. */
    fun undoDelete() {
        val draft = deleted.value ?: return
        undoWindow?.cancel()
        deleted.value = null
        viewModelScope.launch { drafts.upsert(draft) }
    }

    /** The drafts still being made (none kept under a published code) beside the published rows. */
    private fun listed(): Flow<Listed> =
        combine(drafts.observeAll(), published.observeAll()) { stored, rows ->
            val codes = rows.mapTo(HashSet()) { it.code.value }
            Listed(stored.filterNot { it.reservedCode in codes }, rows)
        }.onEach { analyzeNew(it.drafts) }

    private fun analyzeNew(stored: List<Draft>) {
        val current = stored.mapTo(HashSet()) { DraftStamp(it.id, it.updatedAt) }
        requested.retainAll(current)
        verdicts.update { cached -> cached.filterKeys(current::contains) }
        stored.forEach { draft ->
            val stamp = DraftStamp(draft.id, draft.updatedAt)
            if (requested.add(stamp)) analyze(stamp, draft)
        }
    }

    private fun analyze(stamp: DraftStamp, draft: Draft) {
        val editor = games[draft.gameId]?.editor
        if (editor == null) {
            verdicts.update { it + (stamp to null) }
            return
        }
        viewModelScope.launch {
            val verdict = withContext(dispatchers.default) {
                EditorDraft.open(editor, draft.blob).getOrNull()
                    ?.analyze(Budget.within(BUDGET_MS, nanoTime) { !isActive })
            }
            if (stamp in requested) verdicts.update { it + (stamp to verdict) }
        }
    }

    private fun stateOf(listed: Listed, settled: Map<DraftStamp, Verdict?>, view: View, removed: Draft?): CreateUiState {
        val stored = listed.drafts
        val text = view.query
        val game = view.filter
        val searchVisible = stored.size > SEARCH_THRESHOLD
        val needle = text.trim()
        val shownGame = game?.takeIf { searchVisible && stored.any { it.gameId == game } }
        val shown = if (!searchVisible) {
            stored
        } else {
            stored.filter { draft ->
                (shownGame == null || draft.gameId == shownGame) &&
                    (needle.isEmpty() || draft.title.contains(needle, ignoreCase = true))
            }
        }
        val now = clock()
        return CreateUiState(
            loading = false,
            drafts = shown.map { rowOf(it, settled, now) },
            draftCount = stored.size,
            games = newGames,
            searchVisible = searchVisible,
            query = text,
            filters = if (searchVisible) filtersOf(stored) else emptyList(),
            filter = shownGame,
            deleted = removed?.let { CreateUiState.Deleted(it.id, it.title) },
            tab = view.tab,
            published = listed.published.map { publishedRowOf(it, now) },
        )
    }

    private fun rowOf(draft: Draft, settled: Map<DraftStamp, Verdict?>, now: Instant): CreateUiState.DraftRow {
        val stamp = DraftStamp(draft.id, draft.updatedAt)
        val size = editors[draft.gameId]?.sizeLabel(draft.variant) ?: draft.variant.value
        val imported = if (draft.imported) " · imported" else ""
        return CreateUiState.DraftRow(
            id = draft.id,
            gameId = draft.gameId,
            brand = plays.brandOf(draft.gameId),
            title = draft.title,
            meta = "${games.displayName(draft.gameId)} · $size$imported",
            edited = ageLabel("edited", draft.updatedAt, now),
            verdict = if (stamp in settled) settled[stamp] else Verdict.Checking,
        )
    }

    private fun publishedRowOf(row: PublishedPuzzle, now: Instant) = CreateUiState.PublishedRow(
        code = row.code,
        gameId = row.gameId,
        brand = plays.brandOf(row.gameId),
        title = row.title,
        meta = "${games.displayName(row.gameId)} · your time ${formatDuration(row.authorTimeMs)}",
        published = ageLabel("published", row.createdAt, now),
    )

    private fun filtersOf(stored: List<Draft>): List<CreateUiState.GameFilter> =
        stored.groupingBy { it.gameId }.eachCount().map { (id, count) ->
            CreateUiState.GameFilter(id, games.displayName(id), plays.brandOf(id), count)
        }

    private data class DraftStamp(val id: String, val updatedAt: Instant)

    private data class Listed(val drafts: List<Draft>, val published: List<PublishedPuzzle>)

    private data class View(val query: String, val filter: GameId?, val tab: CreateUiState.Tab)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val SEARCH_THRESHOLD = 8
        const val BUDGET_MS = 1_500L
        const val UNDO_WINDOW_MS = 5_000L
    }
}

/** "[verb] just now", "[verb] 5 min ago", "[verb] 2 h ago", "[verb] 3 d ago", aged from [at]. */
private fun ageLabel(verb: String, at: Instant, now: Instant): String {
    val age = Duration.between(at, now).coerceAtLeast(Duration.ZERO)
    return when {
        age.toMinutes() < 1 -> "$verb just now"
        age.toHours() < 1 -> "$verb ${age.toMinutes()} min ago"
        age.toDays() < 1 -> "$verb ${age.toHours()} h ago"
        else -> "$verb ${age.toDays()} d ago"
    }
}
