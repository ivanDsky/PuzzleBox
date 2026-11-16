package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.visibility.HiddenPuzzles
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [HiddenPuzzles]: [hidden] is the persisted state, so a second use case over the same instance stands in for a relaunch. */
class FakeHiddenPuzzles : HiddenPuzzles {

    private val state = MutableStateFlow(emptySet<ShareCode>())

    val hidden: Set<ShareCode> get() = state.value

    override suspend fun codes(): Set<ShareCode> = state.value

    override fun observe(): Flow<Set<ShareCode>> = state

    override suspend fun add(code: ShareCode) {
        state.value += code
    }
}
