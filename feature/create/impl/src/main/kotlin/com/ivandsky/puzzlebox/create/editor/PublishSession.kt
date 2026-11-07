package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.domain.CreateSharedPuzzle
import com.ivandsky.puzzlebox.domain.PublishOutcome
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Studio's Publish: [publish] stores the pending edit first ([flush]), so the shared title
 * and test are the ones on screen, then runs [createShared]. The use case writes the reserved
 * code onto the stored row; [reserved] hands it back to the Studio's one writer, so a later
 * autosave keeps it. One publish at a time.
 *
 * Confined to the main thread, like the Studio's other holders.
 */
class PublishSession(
    private val draftId: String,
    private val createShared: CreateSharedPuzzle,
    private val drafts: DraftRepository,
    private val scope: CoroutineScope,
    private val flush: suspend () -> Unit,
    private val reserved: (code: String?) -> Unit,
) {

    /** [busy] while a publish runs; [outcome] is the last one's, null before the first. */
    data class State(val busy: Boolean = false, val outcome: PublishOutcome? = null)

    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()

    /** Publishes [draft] as checked by [verdict]; a no-op while a publish runs. */
    fun publish(draft: EditorDraft<*>, verdict: Verdict) {
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true)
        scope.launch {
            flush()
            val outcome = createShared(draftId, draft, verdict)
            reserved(drafts.get(draftId)?.reservedCode)
            mutableState.value = State(busy = false, outcome = outcome)
        }
    }
}
