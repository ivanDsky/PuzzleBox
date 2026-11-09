package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/** Every dispatcher of the provider is [dispatcher], so work hopped onto `default` stays on the test's clock. */
internal fun testDispatchers(dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()): DispatcherProvider =
    object : DispatcherProvider {
        override val main = dispatcher
        override val io = dispatcher
        override val default = dispatcher
    }
