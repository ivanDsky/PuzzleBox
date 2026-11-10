package com.ivandsky.puzzlebox.testing.network

import com.ivandsky.puzzlebox.data.network.NetworkStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A [NetworkStatus] the test flips by hand. */
class FakeNetworkStatus(initial: Boolean = true) : NetworkStatus {
    private val state = MutableStateFlow(initial)

    override val online: Flow<Boolean> = state

    fun setOnline(value: Boolean) {
        state.value = value
    }
}
