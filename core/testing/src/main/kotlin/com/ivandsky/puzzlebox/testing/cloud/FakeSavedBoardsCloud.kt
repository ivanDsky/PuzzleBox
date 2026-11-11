package com.ivandsky.puzzlebox.testing.cloud

import com.ivandsky.puzzlebox.data.cloud.CloudResult
import com.ivandsky.puzzlebox.data.cloud.SavedBoardsCloud
import java.util.concurrent.atomic.AtomicInteger

/** Scriptable [SavedBoardsCloud] for UI tests: counts `deleteEverywhere` calls and returns [result]. */
class FakeSavedBoardsCloud : SavedBoardsCloud {

    private val deletes = AtomicInteger()

    /** What `deleteEverywhere` returns; set it per test. */
    @Volatile
    var result: CloudResult<Unit> = CloudResult.Ok(Unit)

    /** How many times `deleteEverywhere` was called. */
    val deleteEverywhereCalls: Int get() = deletes.get()

    override suspend fun deleteEverywhere(): CloudResult<Unit> {
        deletes.incrementAndGet()
        return result
    }
}
