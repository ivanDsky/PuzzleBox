package com.ivandsky.puzzlebox.insights.share

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher

/** Every dispatcher is [dispatcher]: a test decides when the work runs. */
internal class FixedDispatcherProvider(dispatcher: CoroutineDispatcher) : DispatcherProvider {
    override val main = dispatcher
    override val io = dispatcher
    override val default = dispatcher
}
