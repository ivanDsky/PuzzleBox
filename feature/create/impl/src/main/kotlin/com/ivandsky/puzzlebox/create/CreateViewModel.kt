package com.ivandsky.puzzlebox.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.domain.CreateDraft
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
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
 */
class CreateViewModel(
    private val games: GameRegistry,
    private val editors: GameEditorRegistry,
    private val plays: GamePlayRegistry,
    drafts: DraftRepository,
    private val createDraft: CreateDraft,
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

    /** Settled chips by (id, updatedAt); a null verdict is a draft this build cannot open. */
    private val verdicts = MutableStateFlow<Map<DraftStamp, Verdict?>>(emptyMap())

    /** Stamps already analysed or in flight; touched on the main thread only. */
    private val requested = mutableSetOf<DraftStamp>()

    val uiState: StateFlow<CreateUiState> =
        combine(drafts.observeAll().onEach(::analyzeNew), verdicts, query, filter, ::stateOf)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CreateUiState(games = newGames))

    fun onQuery(text: String) {
        query.value = text
    }

    /** Shows only [game]'s drafts; null shows every game. */
    fun onFilter(game: GameId?) {
        filter.value = game
    }

    /** Stores a blank [variant] draft of [gameId] (a choice the sheet offered) and reports its id. */
    fun newPuzzle(gameId: GameId, variant: VariantId, onCreated: (draftId: String) -> Unit) {
        viewModelScope.launch {
            onCreated(createDraft(gameId, variant).id)
        }
    }

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

    private fun stateOf(
        stored: List<Draft>,
        settled: Map<DraftStamp, Verdict?>,
        text: String,
        game: GameId?,
    ): CreateUiState {
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
            edited = editedLabel(draft.updatedAt, now),
            verdict = if (stamp in settled) settled[stamp] else Verdict.Checking,
        )
    }

    private fun filtersOf(stored: List<Draft>): List<CreateUiState.GameFilter> =
        stored.groupingBy { it.gameId }.eachCount().map { (id, count) ->
            CreateUiState.GameFilter(id, games.displayName(id), plays.brandOf(id), count)
        }

    private data class DraftStamp(val id: String, val updatedAt: Instant)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val SEARCH_THRESHOLD = 8
        const val BUDGET_MS = 1_500L
    }
}

/** "edited just now", "edited 5 min ago", "edited 2 h ago", "edited 3 d ago". */
private fun editedLabel(updatedAt: Instant, now: Instant): String {
    val age = Duration.between(updatedAt, now).coerceAtLeast(Duration.ZERO)
    return when {
        age.toMinutes() < 1 -> "edited just now"
        age.toHours() < 1 -> "edited ${age.toMinutes()} min ago"
        age.toDays() < 1 -> "edited ${age.toHours()} h ago"
        else -> "edited ${age.toDays()} d ago"
    }
}
