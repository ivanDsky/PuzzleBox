package com.ivandsky.puzzlebox.backend.firebase

import com.ivandsky.puzzlebox.data.cloud.CloudDoc
import kotlinx.coroutines.awaitCancellation

/**
 * In-memory [CloudSpaceStore] that behaves like the server: a put set-merges and stamps `updatedAtMs` from [clock], a
 * read returns the docs oldest update first, and every call is recorded in [calls]. [failNext] and [hangNext] script
 * what a device cannot force on demand; [afterCommit] runs after each commit, where a test can switch the account between two batches.
 */
internal class FakeCloudSpaceStore(var clock: () -> Long = { 0L }) : CloudSpaceStore {

    /** One call that reached the store: its name, the kind it was for and how many docs or ids it carried. */
    data class Call(val name: String, val kind: String, val size: Int)

    val calls = mutableListOf<Call>()
    var afterCommit: () -> Unit = {}

    private val stored = HashMap<Pair<String, String>, MutableMap<String, CloudDoc>>()
    private val script = ArrayDeque<Throwable?>()

    /** The next call throws [error]; calls queue in order with [hangNext]. */
    fun failNext(error: Throwable) {
        script += error
    }

    /** The next call suspends until it is cancelled, like a commit that never answers offline. */
    fun hangNext() {
        script += null
    }

    fun docs(uid: String, kind: String): List<CloudDoc> = sorted(uid, kind)

    private suspend fun enter(name: String, kind: String, size: Int) {
        calls += Call(name, kind, size)
        if (script.isNotEmpty()) script.removeFirst()?.let { throw it } ?: awaitCancellation()
    }

    override suspend fun read(uid: String, kind: String, cursorMs: Long?): List<CloudDoc> {
        enter("read", kind, 0)
        return sorted(uid, kind).filter { cursorMs == null || checkNotNull(it.updatedAtMs) >= cursorMs }
    }

    override suspend fun ids(uid: String, kind: String): List<String> {
        enter("ids", kind, 0)
        return sorted(uid, kind).map { it.id }
    }

    override suspend fun commitPut(uid: String, kind: String, docs: List<CloudDoc>) {
        require(docs.size <= CLOUD_BATCH_LIMIT) { "batch of ${docs.size}" }
        enter("put", kind, docs.size)
        val space = stored.getOrPut(uid to kind) { LinkedHashMap() }
        docs.forEach { doc ->
            toFirestoreMap(doc.fields)
            space[doc.id] = CloudDoc(doc.id, merge(space[doc.id]?.fields.orEmpty(), doc.fields), clock())
        }
        afterCommit()
    }

    override suspend fun commitDelete(uid: String, kind: String, ids: List<String>) {
        require(ids.size <= CLOUD_BATCH_LIMIT) { "batch of ${ids.size}" }
        enter("delete", kind, ids.size)
        ids.forEach { stored[uid to kind]?.remove(it) }
        afterCommit()
    }

    private fun sorted(uid: String, kind: String): List<CloudDoc> =
        stored[uid to kind].orEmpty().values.sortedWith(compareBy({ it.updatedAtMs }, { it.id }))

    private fun merge(base: Map<String, Any?>, patch: Map<String, Any?>): Map<String, Any?> {
        val merged = LinkedHashMap(base)
        for ((key, value) in patch) {
            val old = merged[key]
            merged[key] = if (old is Map<*, *> && value is Map<*, *>) merge(old.fields(), value.fields()) else value
        }
        return merged
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<*, *>.fields(): Map<String, Any?> = this as Map<String, Any?>
}
