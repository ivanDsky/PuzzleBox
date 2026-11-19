package com.ivandsky.puzzlebox.testing.insights

import com.ivandsky.puzzlebox.data.insights.RecapDismissals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [RecapDismissals]: [dismissed] is the persisted state, so a second reader over the same instance stands in for a relaunch. */
class FakeRecapDismissals(initial: Set<String> = emptySet()) : RecapDismissals {

    private val state = MutableStateFlow(initial)

    val dismissed: Set<String> get() = state.value

    override fun observe(): Flow<Set<String>> = state

    override suspend fun dismiss(periodId: String) {
        state.value += periodId
    }
}
