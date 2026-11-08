package com.ivandsky.puzzlebox.data.repository

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [SharedTextStore]: the shared text is transient, so after process death the user
 * shares again. [take] is an atomic get-and-set to null, so the Create root consumes the text
 * exactly once even under recomposition races.
 */
internal class InMemorySharedTextStore : SharedTextStore {

    private val pendingFlow = MutableStateFlow<String?>(null)
    private val take = AtomicReference<String?>(null)

    override val pending: StateFlow<String?> get() = pendingFlow.asStateFlow()

    override fun put(text: String) {
        take.set(text)
        pendingFlow.value = text
    }

    override fun take(): String? = take.getAndSet(null).also { pendingFlow.value = null }
}
