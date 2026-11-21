package com.ivandsky.puzzlebox.testing.insights

import com.ivandsky.puzzlebox.data.insights.RecapOpenings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [RecapOpenings]: [opened] is the persisted state, so a second reader over the same instance stands in for a relaunch. */
class FakeRecapOpenings(initial: Set<String> = emptySet()) : RecapOpenings {

    private val state = MutableStateFlow(initial)

    /** Every period id marked so far, in the order first marked. */
    val opened: Set<String> get() = state.value

    /** How many times [markOpened] was called, repeats included. */
    var writes = 0
        private set

    override fun observe(): Flow<Set<String>> = state

    override suspend fun markOpened(periodId: String) {
        writes++
        state.value += periodId
    }
}
