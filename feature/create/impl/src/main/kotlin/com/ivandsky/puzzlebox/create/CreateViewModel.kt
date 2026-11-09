package com.ivandsky.puzzlebox.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.create.importtext.ImportTextState
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.data.repository.PublishedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SharedTextStore
import com.ivandsky.puzzlebox.domain.CreateDraft
import com.ivandsky.puzzlebox.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.domain.ImportDraftFromText
import com.ivandsky.puzzlebox.domain.SyncSolvesCounts
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Create root: lists the stored drafts and starts new ones. The New puzzle sheet and the
 * empty state offer every game with an editor UI ([GameEditorRegistry.ids], registration
 * order) whose plugin carries the editor, each at exactly its editor's variants (spec A1, A5)
 * with the middle one as its default size, under its [GamePlayRegistry] brand, while its
 * [EditorReleaseFlags] flag (if any) is on.
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
 * search and its verdict chips. Each time that tab shows, [syncSolves] refreshes the rows' solve
 * counts in the background; the list never waits for it, and the cached counts show meanwhile.
 *
 * [importText] owns the Import from text sheet; the header pill shows while [importEnabled]
 * ([ReleaseFlag.TextImport]). An imported draft is sent on [created], like a new one. A Studio's
 * import hands its draft to the next Studio through [importedNotice]. Text another app shared
 * ([SharedTextStore]) goes through that same sheet while the root is resumed
 * ([importSharedText]).
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
    private val syncSolves: SyncSolvesCounts,
    private val releaseFlags: ReleaseFlags,
    private val sharedText: SharedTextStore,
    private val clock: () -> Instant,
    private val nanoTime: () -> Long = System::nanoTime,
) : ViewModel() {

    private val editorGames: List<CreateUiState.NewGame> = editors.ids.mapNotNull { id ->
        val editor = games[id]?.editor ?: return@mapNotNull null
        val ui = editors[id] ?: return@mapNotNull null
        val sizes = editor.variants.map { CreateUiState.Size(it, ui.sizeLabel(it)) }
        if (sizes.isEmpty()) return@mapNotNull null
        CreateUiState.NewGame(
            id = id,
            name = games.displayName(id),
            brand = plays.brandOf(id),
            rule = gameRuleLine(id),
            defaultSize = sizes.first { it.variant == editor.defaultVariant },
            sizes = sizes,
        )
    }

    private fun offered(flags: Set<ReleaseFlag>) =
        editorGames.filter { EditorReleaseFlags.flagFor(it.id)?.let(flags::contains) ?: true }

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow<GameId?>(null)
    private val tab = MutableStateFlow(CreateUiState.Tab.Drafts)

    private val analysis = DraftAnalysis(viewModelScope, games, dispatchers, nanoTime)

    /** The last deleted draft while its Undo is offered. */
    private val deleted = MutableStateFlow<Draft?>(null)
    private var undoWindow: Job? = null
    private var creating: Job? = null
    private var syncing: Job? = null

    val uiState: StateFlow<CreateUiState> =
        combine(
            listed(),
            analysis.verdicts,
            combine(query, filter, tab.onEach(::syncWhenPublished), ::View),
            deleted,
            releaseFlags.enabled.map(::offered),
        ) { listed, settled, view, removed, newGames ->
            stateOf(listed, settled, view, removed, newGames, clock(), games, editors, plays)
        }.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CreateUiState(games = offered(releaseFlags.enabled.value)),
        )

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

    /** The id of each draft [newPuzzle] or the import sheet stored, once each, for the route to open. */
    val created: Flow<String> = createdIds.receiveAsFlow()

    /** Whether the header's Import pill shows: [ReleaseFlag.TextImport] is on. */
    val importEnabled: StateFlow<Boolean> = releaseFlags.enabled.map { ReleaseFlag.TextImport in it }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ReleaseFlag.TextImport in releaseFlags.enabled.value,
    )

    /** The Import from text sheet; an imported draft is sent on [created] like a new one. */
    val importText = ImportTextState(
        importDraft = ImportDraftFromText(games, createDraft, drafts, dispatchers),
        scope = viewModelScope,
        gameName = { games.displayName(GameId(it)) },
        onImported = { createdIds.trySend(it) },
    )

    /**
     * Imports the text another app shared, the way a paste would: each text waiting in
     * [SharedTextStore] is taken once, then [importText] opens the sheet and submits it, so a
     * good one is sent on [created] and an error stays on the open sheet. A text that arrives
     * during a check stays in the store until that check ends, so two shares import one after
     * the other. Runs until cancelled; the Create root runs it while it is resumed, so nothing
     * is taken while another screen covers it or a transition is still settling.
     */
    suspend fun importSharedText() {
        sharedText.pending.filterNotNull().collect {
            importText.ui.first { !it.checking }
            // No suspension from here to submit, so a cancel never splits take from import.
            val text = sharedText.take() ?: return@collect
            importText.open()
            importText.submit(text)
        }
    }

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

    private val importedDraft = MutableStateFlow<String?>(null)

    /**
     * The id of the draft a Studio's Import from text just stored, until the Studio that opens
     * it takes the notice with [consumeImportedNotice]. This ViewModel outlives the Studio that
     * imported (it is the activity's, like the delete Undo), so the notice crosses the swap.
     */
    val importedNotice: StateFlow<String?> = importedDraft.asStateFlow()

    /** Records that [draftId] was just imported from a Studio; its Studio will show the toast. */
    fun noteImported(draftId: String) {
        importedDraft.value = draftId
    }

    /** Clears the notice for [draftId] only, so a newer import's notice is never lost. */
    fun consumeImportedNotice(draftId: String) {
        importedDraft.compareAndSet(draftId, null)
    }

    /** The Published tab showing (on a switch or a return to Create) asks for fresh counts; one sync at a time. */
    private fun syncWhenPublished(shown: CreateUiState.Tab) {
        if (shown != CreateUiState.Tab.Published || syncing?.isActive == true) return
        syncing = viewModelScope.launch { syncSolves() }
    }

    /** The drafts still being made (none kept under a published code) beside the published rows. */
    private fun listed(): Flow<Listed> =
        combine(drafts.observeAll(), published.observeAll()) { stored, rows ->
            val codes = rows.mapTo(HashSet()) { it.code.value }
            Listed(stored.filterNot { it.reservedCode in codes }, rows)
        }.onEach { analysis.analyzeNew(it.drafts) }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val UNDO_WINDOW_MS = 5_000L
    }
}
