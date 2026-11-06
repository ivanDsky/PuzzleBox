package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Studio's live verdict (spec A7, ADR-0019 §4): each [submit] shows [Verdict.Checking] at
 * once, waits [debounceMs] for the author to stop, then analyzes on [DispatcherProvider.default]
 * under a [budgetMs] budget, so a solve past it settles on [Verdict.Undetermined]. A newer
 * submit cancels the older one, debouncing or solving; cancellation reaches the solver through
 * the budget, and a cancelled solve never publishes. [submit] and the publish run on [scope]'s
 * dispatcher (the VM's main), so the newest submit always has the last word.
 */
internal class VerdictPipeline(
    private val scope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val nanoTime: () -> Long = System::nanoTime,
    private val debounceMs: Long = 150,
    private val budgetMs: Long = 1_500,
) {
    private val mutableVerdict = MutableStateFlow<Verdict>(Verdict.Checking)
    val verdict: StateFlow<Verdict> = mutableVerdict.asStateFlow()

    private var pending: Job? = null

    fun submit(draft: EditorDraft<*>) {
        pending?.cancel()
        mutableVerdict.value = Verdict.Checking
        pending = scope.launch {
            delay(debounceMs)
            val verdict = withContext(dispatchers.default) {
                draft.analyze(Budget.within(budgetMs, nanoTime) { !isActive })
            }
            mutableVerdict.value = verdict
        }
    }
}
