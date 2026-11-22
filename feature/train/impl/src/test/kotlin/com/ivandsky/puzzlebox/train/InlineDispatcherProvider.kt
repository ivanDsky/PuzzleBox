package com.ivandsky.puzzlebox.train

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable

/** Every dispatcher runs its work at once on the caller's thread, so a test sees each step's result when the call returns. */
internal object InlineDispatcherProvider : DispatcherProvider {
    private val inline = object : CoroutineDispatcher() {
        override fun isDispatchNeeded(context: CoroutineContext) = false
        override fun dispatch(context: CoroutineContext, block: Runnable) = block.run()
    }
    override val main: CoroutineDispatcher = inline
    override val io: CoroutineDispatcher = inline
    override val default: CoroutineDispatcher = inline
}
