package com.ivandsky.puzzlebox.create

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A draft as of one edit: its verdict is valid until [updatedAt] changes. */
internal data class DraftStamp(val id: String, val updatedAt: Instant)

/**
 * The Create root's verdict chips (spec A7): `analyze(draft, Budget.within(1_500…))` on
 * [DispatcherProvider.default], once per [DraftStamp]. [analyzeNew] is called on the main
 * thread with the drafts now listed; it drops the stamps of drafts no longer listed, so an
 * edited draft is analysed again and a restored one too.
 */
internal class DraftAnalysis(
    private val scope: CoroutineScope,
    private val games: GameRegistry,
    private val dispatchers: DispatcherProvider,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val settled = MutableStateFlow<Map<DraftStamp, Verdict?>>(emptyMap())

    /** Stamps already analysed or in flight; touched on the main thread only. */
    private val requested = mutableSetOf<DraftStamp>()

    /** Settled chips by (id, updatedAt); a null verdict is a draft this build cannot open. */
    val verdicts: StateFlow<Map<DraftStamp, Verdict?>> = settled.asStateFlow()

    fun analyzeNew(stored: List<Draft>) {
        val current = stored.mapTo(HashSet()) { DraftStamp(it.id, it.updatedAt) }
        requested.retainAll(current)
        settled.update { cached -> cached.filterKeys(current::contains) }
        stored.forEach { draft ->
            val stamp = DraftStamp(draft.id, draft.updatedAt)
            if (requested.add(stamp)) analyze(stamp, draft)
        }
    }

    private fun analyze(stamp: DraftStamp, draft: Draft) {
        val editor = games[draft.gameId]?.editor
        if (editor == null) {
            settled.update { it + (stamp to null) }
            return
        }
        scope.launch {
            val verdict = withContext(dispatchers.default) {
                EditorDraft.open(editor, draft.blob).getOrNull()
                    ?.analyze(Budget.within(BUDGET_MS, nanoTime) { !isActive })
            }
            if (stamp in requested) settled.update { it + (stamp to verdict) }
        }
    }

    private companion object {
        const val BUDGET_MS = 1_500L
    }
}
