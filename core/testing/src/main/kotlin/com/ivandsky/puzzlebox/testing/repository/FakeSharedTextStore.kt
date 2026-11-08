package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.repository.SharedTextStore
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [SharedTextStore]. It passes the same contract test as the production adapter, so
 * the two cannot drift.
 */
class FakeSharedTextStore : SharedTextStore {

    private val pendingFlow = MutableStateFlow<String?>(null)
    private val take = AtomicReference<String?>(null)

    override val pending: StateFlow<String?> get() = pendingFlow.asStateFlow()

    override fun put(text: String) {
        take.set(text)
        pendingFlow.value = text
    }

    override fun take(): String? = take.getAndSet(null).also { pendingFlow.value = null }
}
