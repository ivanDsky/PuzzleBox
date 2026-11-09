package com.ivandsky.puzzlebox.create.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.ShippedReleaseFlags
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.importtext.ImportTextState
import com.ivandsky.puzzlebox.create.published.UnpublishSession
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.data.repository.PublishedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.domain.CreateSharedPuzzle
import com.ivandsky.puzzlebox.domain.PublishOutcome
import com.ivandsky.puzzlebox.domain.UpdatePublishState
import com.ivandsky.puzzlebox.domain.ValidatePublish
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Studio's thin composition root: opens the stored draft named by [key], holds its
 * [EditorDraft], hands every edit to its holders and autosaves it with its undo history
 * ([saving]; stored at once on [flush], before [duplicate], [delete] and [publish] and on
 * leaving the Studio). [saves] must outlive this ViewModel: [viewModelScope] is already
 * cancelled when [onCleared] queues the last save. Once a publish lands the draft is
 * read-only (reopened too, while [publishedPuzzles] holds its reserved code): its [ticket] shows, and edits, undo, redo, resize, clear, rename and the publish
 * details change nothing. [unpublish] takes the code down; the draft stays the read-only record
 * of what was published, its ticket struck. The overflow's Import from text ([importText], offered
 * while [importEnabled]) stores a new imported draft and leaves the open one as it is.
 */
class EditorViewModel(
    private val key: EditorKey,
    private val drafts: DraftRepository,
    private val games: GameRegistry,
    private val editors: GameEditorRegistry,
    private val plays: GamePlayRegistry,
    copyDraft: CreateDraftCopy,
    dispatchers: DispatcherProvider,
    private val saves: CoroutineScope,
    private val clock: () -> Instant,
    createShared: CreateSharedPuzzle,
    private val publishedPuzzles: PublishedPuzzleRepository,
    identity: IdentityService,
    idTokens: GoogleIdTokenProvider,
    updatePublishState: UpdatePublishState,
    private val preferences: UserPreferencesRepository,
    private val validatePublish: ValidatePublish = ValidatePublish(),
    releaseFlags: ReleaseFlags = ShippedReleaseFlags,
) : ViewModel() {

    private val mutableState = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val state: StateFlow<EditorUiState> = mutableState.asStateFlow()

    private val loadDraft = LoadDraft(drafts, games, editors)
    private val verdicts = VerdictPipeline(viewModelScope, dispatchers)
    private val history = EditHistory(locked = { published }, adopt = ::adopt)

    /** The saved Auto-show: each new Multiple opens with its witness on the board while it is on (the default). */
    val autoShowWitness: StateFlow<Boolean> =
        preferences.autoShowWitness.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = true)

    /** The verdict card; Check again resubmits the board as it stands; its witness choice goes to the board's controller. */
    val verdictCard = VerdictCardState(
        recheck = { (state.value as? EditorUiState.Ready)?.controller?.draft?.value?.let(verdicts::recheck) },
        controller = { (state.value as? EditorUiState.Ready)?.controller },
        autoShow = { autoShowWitness.value },
    )

    /** The draft's title, rename, duplicate and delete. */
    val meta = DraftMeta(key.draftId, copyDraft, viewModelScope, locked = { published }) { title -> lastStored?.let { write(it.copy(title = title)) } }

    /** The required test solve: its sandbox, the test valid for the board, and the clear toast. */
    val testSolve = TestSolveSession(key.draftId, plays, games, dispatchers, viewModelScope) { test -> lastStored?.let { write(it.copy(testSolve = test)) } }

    /** Undo and Redo are enabled only when the open draft has a step to move. */
    val canUndo: StateFlow<Boolean> = history.canUndo
    val canRedo: StateFlow<Boolean> = history.canRedo

    /** The stored row as last written; null until the draft has loaded. */
    private var lastStored: Draft? = null

    /** The board as last recorded: a published draft puts it back over any edit that slips through. */
    private var board: EditorDraft<*>? = null

    private val autosave = DraftAutosave(saves, drafts::upsert)

    /** "Saving…" while an edit waits to be stored, "✓ Saved" once it is. */
    val saving: StateFlow<Boolean> = autosave.saving

    /** Publish and its details: the pending edit is stored first; the reserved code goes onto the row this writes. */
    val publish = PublishSession(
        draftId = key.draftId,
        createShared = createShared,
        identity = identity,
        idTokens = idTokens,
        scope = viewModelScope,
        flush = { autosave.flush() },
        reserved = { code -> lastStored = lastStored?.copy(reservedCode = code)?.also(autosave::offer) },
        storeDetails = { authorName, note -> lastStored?.let { write(it.copy(authorName = authorName, note = note)) } },
    )

    /** The published draft's share ticket; null until a publish lands. */
    internal val ticket: StateFlow<ShareTicket?> =
        studioTicket(publish.state, meta.title, testSolve.passed, mutableState, publishedPuzzles, games, plays, viewModelScope)

    /** Unpublish and its confirm dialog. */
    internal val unpublish = UnpublishSession(updatePublishState, viewModelScope)

    private val studioImport = StudioImport(games, drafts, dispatchers, clock, releaseFlags, viewModelScope)

    /** The overflow's Import from text sheet; a draft it imports is sent on [importedDrafts]. */
    val importText: ImportTextState = studioImport.text
    val importEnabled: StateFlow<Boolean> = studioImport.enabled
    val importedDrafts: Flow<String> = studioImport.imported

    init {
        viewModelScope.launch {
            mutableState.value = load()
            (mutableState.value as? EditorUiState.Ready)?.let { verdictCard.read(it.verdict) }
        }
        viewModelScope.launch {
            verdicts.verdict.collect { verdict ->
                val ready = mutableState.value as? EditorUiState.Ready ?: return@collect
                mutableState.value = ready.copy(verdict = verdict)
                verdictCard.read(verdict)
            }
        }
    }

    /** The code [stored] was published under: its reserved code, once a published row holds it. */
    private suspend fun publishedCode(stored: Draft): ShareCode? =
        stored.reservedCode?.let(ShareCode::parse)?.takeIf { publishedPuzzles.observe(it).first() != null }

    private suspend fun load(): EditorUiState {
        val (stored, draft, ui) = loadDraft(key.draftId) ?: return EditorUiState.Missing
        lastStored = stored
        meta.open(stored.title)
        publish.open(stored.authorName, stored.note, published = publishedCode(stored))
        history.open(draft)
        board = draft
        testSolve.open(stored.testSolve, draft)
        // The verdict lands after the debounce; it is only published once this state is Ready.
        verdicts.submit(draft)
        return EditorUiState.Ready(
            subtitle = subtitleOf(draft, ui),
            verdict = verdicts.verdict.value,
            editor = ui,
            controller = ui.createController(draft, ::onEdit),
            imported = stored.imported,
        )
    }

    /** The card's Auto-show switch: saved for every editor and later sessions; the next new Multiple reads it. */
    fun setAutoShowWitness(on: Boolean) {
        viewModelScope.launch { preferences.setAutoShowWitness(on) }
    }

    /**
     * The test-cleared toast's Undo: back to the nearest earlier board with the tested layout, so
     * the test passes again even when aid-only steps followed the edit that cleared it.
     */
    fun restoreTest() {
        testSolve.dismissCleared()
        history.undoTo(testSolve::isTested)
    }

    /** One step back: the board adopts it, then it is checked and stored like any edit. */
    fun undo() = history.undo()

    /** The undone step again, handled like [undo]. */
    fun redo() = history.redo()

    /** A blank [variant] board as one undoable step (A5); picking the open size is not a step. */
    fun resize(variant: VariantId) = history.resize(variant)

    /** A blank board of the open size as one undoable step (A5). */
    fun clear() = history.clear()

    /** The overflow's Unpublish, once confirmed in its dialog. */
    fun confirmUnpublish() {
        val code = (publish.state.value.outcome as? PublishOutcome.Published)?.code ?: return
        unpublish.confirm(code)
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

    /**
     * Delete stores the pending edit and takes no more, then hands the id on: the Create root's
     * Undo restores the board the author last saw, and no late save brings the draft back.
     */
    fun delete(onDelete: (draftId: String) -> Unit) {
        saves.launch(start = CoroutineStart.UNDISPATCHED) {
            autosave.finish()
            meta.delete(onDelete)
        }
    }

    /** Back, a tab's stack dropping the Studio, Duplicate's replace: the last edit is stored. */
    override fun onCleared() {
        saves.launch { autosave.flush() }
    }

    /** [history]'s moves (undo, redo, resize, clear) replace the board; only these can change its size, so the subtitle follows here. */
    private fun adopt(draft: EditorDraft<*>) {
        (mutableState.value as? EditorUiState.Ready)?.let { ready ->
            ready.controller.adopt(draft)
            mutableState.value = ready.copy(subtitle = subtitleOf(draft, ready.editor))
        }
        board = draft
        changed(draft)
    }

    private fun subtitleOf(draft: EditorDraft<*>, ui: GameEditorUi): String =
        "${games.displayName(draft.gameId)} ${ui.sizeLabel(draft.variant)}"

    /** The readiness line's blocks (33): what still keeps the open board, title and test from publishing. */
    fun readiness(draft: EditorDraft<*>, verdict: Verdict, title: String, passed: TestSolve?): List<PublishBlock> =
        validatePublish(draft, verdict, title, passed)

    /** A board edit; a published board takes none, so its controller is put back where it was. */
    private fun onEdit(draft: EditorDraft<*>) {
        if (published) {
            board?.let { (mutableState.value as? EditorUiState.Ready)?.controller?.adopt(it) }
            return
        }
        board = draft
        history.record(draft)
        changed(draft)
    }

    private val published: Boolean get() = publish.state.value.outcome is PublishOutcome.Published

    private fun changed(draft: EditorDraft<*>) {
        verdictCard.dismiss()
        verdicts.submit(draft)
        testSolve.follow(draft)
        val base = lastStored ?: return
        write(base.copy(variant = draft.variant, draftFormatVersion = draft.editor.draftFormatVersion, blob = draft.encode()))
    }

    /** Stamps [next] and hands it to the autosave. */
    private fun write(next: Draft) {
        val stamped = next.copy(updatedAt = clock())
        lastStored = stamped
        autosave.offer(stamped)
    }
}
