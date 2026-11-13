package com.ivandsky.puzzlebox.database

import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * The one thread every Robolectric Room test runs its queries on: `builder.setQueryExecutor(RoomQueryThread.executor)`.
 *
 * Robolectric's native runtime opens every host CursorWindow through libc `tmpfile()`. On macOS that call sets the
 * signal mask of every thread in the process, so two overlapping cursor windows leave every thread with SIGSEGV
 * blocked for good, and the next safepoint poll or null check in compiled code spins forever, hanging the test JVM.
 * Room's default pool has four threads; one shared thread means no two windows open at once, across databases too.
 *
 * Transactions share it too (Room uses the query executor when no transaction executor is set): a second thread
 * for them would bring back two concurrent windows. A transaction keeps the thread and runs its own queries on it,
 * so other queries wait behind it and nothing waits on itself.
 *
 * Test-only: production Room configuration is unchanged. This is the core:database copy of core:testing's
 * RoomQueryThread, which this module's tests cannot depend on; the source scan test keeps them from drifting apart.
 */
object RoomQueryThread {
    val executor: Executor = Executors.newSingleThreadExecutor { Thread(it, "test-room-query").apply { isDaemon = true } }
}
