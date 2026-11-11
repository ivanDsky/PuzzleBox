package com.ivandsky.puzzlebox.testing.cloud

import com.ivandsky.puzzlebox.data.cloud.SyncStateStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** In-memory [SyncStateStore] with the real store's semantics: per-uid isolation, day-reset counter, [forget] keeps the last uid. */
class FakeSyncStateStore(lastUid: String? = null) : SyncStateStore {

    private data class Writes(val day: String, val count: Int)

    private val cursors = MutableStateFlow(emptyMap<Pair<String, String>, Long>())
    private val shown = MutableStateFlow(emptySet<String>())
    private val writes = MutableStateFlow(emptyMap<String, Writes>())
    private val last = MutableStateFlow(lastUid)

    override val lastUid: StateFlow<String?> = last

    override suspend fun cursor(uid: String, kind: String): Long? = cursors.value[uid to kind]

    override suspend fun setCursor(uid: String, kind: String, cursorMs: Long) {
        cursors.update { it + ((uid to kind) to cursorMs) }
    }

    override suspend fun setLastUid(uid: String) {
        last.value = uid
    }

    override suspend fun restoreShown(uid: String): Boolean = uid in shown.value

    override suspend fun markRestoreShown(uid: String) {
        shown.update { it + uid }
    }

    override suspend fun countWrites(uid: String, day: String, n: Int): Int {
        var total = 0
        writes.update { all ->
            val before = all[uid]
            total = (if (before?.day == day) before.count else 0) + n
            all + (uid to Writes(day, total))
        }
        return total
    }

    override suspend fun forget(uid: String) {
        cursors.update { all -> all.filterKeys { it.first != uid } }
        shown.update { it - uid }
    }
}
