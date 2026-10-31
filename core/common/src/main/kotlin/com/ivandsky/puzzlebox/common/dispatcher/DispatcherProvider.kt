package com.ivandsky.puzzlebox.common.dispatcher

import kotlinx.coroutines.CoroutineDispatcher

/** Injection seam for coroutine dispatchers so callers stay testable. */
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}
