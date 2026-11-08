package com.ivandsky.puzzlebox.games.strandsui.editor

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext

/** A dispatcher that holds its work until [runAll]; nothing runs behind the test's back. */
class QueueDispatcher : CoroutineDispatcher() {
    private val queue = ArrayDeque<Runnable>()

    val pending: Int get() = queue.size

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        queue.addLast(block)
    }

    /** Runs queued work, including work it queues, until none is left. */
    fun runAll() {
        while (queue.isNotEmpty()) queue.removeFirst().run()
    }
}

/** Every dispatcher of the provider is [queue]. */
class QueueDispatcherProvider(queue: QueueDispatcher) : DispatcherProvider {
    override val main: CoroutineDispatcher = queue
    override val io: CoroutineDispatcher = queue
    override val default: CoroutineDispatcher = queue
}
