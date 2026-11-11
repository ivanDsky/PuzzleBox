package com.ivandsky.puzzlebox.testing.cloud

import com.ivandsky.puzzlebox.data.cloud.CloudDoc
import com.ivandsky.puzzlebox.data.cloud.CloudResult
import com.ivandsky.puzzlebox.data.cloud.CloudSpace
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import kotlinx.coroutines.awaitCancellation

/**
 * In-memory [CloudSpace] that passes [com.ivandsky.puzzlebox.testing.contract.CloudSpaceContract], plus knobs for what a
 * device cannot force on demand. The signed-in uid comes from [identity]; a call for another uid (or with nobody signed
 * in) is [CloudResult.WrongAccount] and touches nothing. `updatedAtMs` is stamped from [clock], the caller's value is
 * ignored. Docs, payloads and results are copied in and out, so a caller can never mutate the stored state.
 *
 * Counters follow the Firestore bill: [reads] one per returned doc and at least one per query, [writes] one per doc,
 * [deletes] one per requested id (one per removed doc for `deleteAll`, whose enumeration is a query too). A call that is
 * scripted to fail counts nothing and changes nothing.
 */
class FakeCloudSpace(
    private val identity: FakeIdentityService,
    private val clock: () -> Long = System::currentTimeMillis,
) : CloudSpace {

    /** One recorded `put` doc, exactly as the caller passed it. */
    data class Written(val uid: String, val kind: String, val doc: CloudDoc)

    private class Scripted(val result: CloudResult<Nothing>?)

    private val lock = Any()
    private val stored = HashMap<Pair<String, String>, MutableMap<String, CloudDoc>>()
    private val script = ArrayDeque<Scripted>()
    private val written = mutableListOf<Written>()

    private var readCount = 0
    private var writeCount = 0
    private var deleteCount = 0

    val reads: Int get() = synchronized(lock) { readCount }
    val writes: Int get() = synchronized(lock) { writeCount }
    val deletes: Int get() = synchronized(lock) { deleteCount }

    /** Every doc `put` was asked to write, in order, as passed in (before merging and stamping). */
    val payloads: List<Written> get() = synchronized(lock) { written.map { it.copy(doc = it.doc.detached()) } }

    /** The stored docs of [kind] for [uid], oldest update first. */
    fun docs(uid: String, kind: String): List<CloudDoc> = synchronized(lock) { sorted(uid, kind) }

    /** True when [uid] has no stored doc of any kind. */
    fun isEmpty(uid: String): Boolean = synchronized(lock) { stored.none { (key, docs) -> key.first == uid && docs.isNotEmpty() } }

    /** The next call, whatever it is, returns [result] and does nothing else. Calls queue in order with [hangNext]. */
    fun failNext(result: CloudResult<Nothing>) {
        synchronized(lock) { script += Scripted(result) }
    }

    /** The next call suspends until it is cancelled, like a request that times out. */
    fun hangNext() {
        synchronized(lock) { script += Scripted(null) }
    }

    override suspend fun changedSince(uid: String, kind: String, cursorMs: Long?): CloudResult<List<CloudDoc>> =
        call(uid) {
            val docs = sorted(uid, kind).filter { cursorMs == null || checkNotNull(it.updatedAtMs) >= cursorMs }
            readCount += maxOf(1, docs.size)
            CloudResult.Ok(docs)
        }

    override suspend fun put(uid: String, kind: String, docs: List<CloudDoc>): CloudResult<Unit> = call(uid) {
        docs.forEach { requirePlain(it.fields) }
        val now = clock()
        val space = stored.getOrPut(uid to kind) { LinkedHashMap() }
        docs.forEach { doc ->
            written += Written(uid, kind, doc.detached())
            val merged = space[doc.id]?.fields.orEmpty().let { mergeMaps(it, doc.fields) }
            space[doc.id] = CloudDoc(doc.id, merged, now)
            writeCount++
        }
        CloudResult.Ok(Unit)
    }

    override suspend fun delete(uid: String, kind: String, ids: List<String>): CloudResult<Unit> = call(uid) {
        val space = stored[uid to kind]
        ids.forEach {
            space?.remove(it)
            deleteCount++
        }
        CloudResult.Ok(Unit)
    }

    override suspend fun deleteAll(uid: String, kinds: List<String>): CloudResult<Unit> = call(uid) {
        kinds.forEach { kind ->
            val removed = stored.remove(uid to kind)?.size ?: 0
            readCount += maxOf(1, removed)
            deleteCount += removed
        }
        CloudResult.Ok(Unit)
    }

    /** The scripted outcome first, then the account check, then [block] under the lock. */
    private suspend fun <T> call(uid: String, block: () -> CloudResult<T>): CloudResult<T> {
        val scripted = synchronized(lock) { script.removeFirstOrNull() }
        if (scripted != null) return scripted.result ?: awaitCancellation()
        return synchronized(lock) {
            if (identity.currentIdentity?.uid == uid) block() else CloudResult.WrongAccount
        }
    }

    private fun sorted(uid: String, kind: String): List<CloudDoc> =
        stored[uid to kind].orEmpty().values.sortedWith(compareBy({ it.updatedAtMs }, { it.id })).map { it.detached() }

    private fun mergeMaps(base: Map<String, Any?>, patch: Map<String, Any?>): Map<String, Any?> {
        val merged = LinkedHashMap(base)
        for ((key, value) in patch) {
            val old = merged[key]
            merged[key] = if (old is Map<*, *> && value is Map<*, *>) mergeMaps(old.asFields(), value.asFields()) else copyValue(value)
        }
        return merged
    }

    private fun requirePlain(value: Any?) {
        when (value) {
            null, is String, is Long, is Boolean, is Double, is ByteArray -> Unit
            is List<*> -> value.forEach(::requirePlain)
            is Map<*, *> -> value.forEach { (key, nested) ->
                require(key is String) { "map keys must be String, got ${key?.let { it::class.simpleName }}" }
                requirePlain(nested)
            }
            else -> throw IllegalArgumentException("not a plain cloud value: ${value::class.simpleName}")
        }
    }

    private fun CloudDoc.detached() = copy(fields = copyValue(fields).asFields())

    private fun copyValue(value: Any?): Any? = when (value) {
        is ByteArray -> value.copyOf()
        is List<*> -> value.map(::copyValue)
        is Map<*, *> -> value.asFields().mapValuesTo(LinkedHashMap()) { copyValue(it.value) }
        else -> value
    }

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asFields(): Map<String, Any?> = this as Map<String, Any?>
}
