package com.ivandsky.puzzlebox.data.cloud

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A [SyncedKind] for engine tests: rows live in memory, every call is appended to [events] ("adopt drafts ann",
 * "apply drafts ann d1,d2", "pending drafts ann", "acknowledge drafts ann d1,d2", "forget drafts ann"), and [onCall] runs first in every call so a
 * test can flip the identity, hang or start another sync mid-run. Pass one [events] list to several kinds to see
 * their interleaving.
 *
 * A row is a map of fields; [edit] changes one locally and leaves it pending until `acknowledge`. `apply` stores the
 * cloud's doc unless the local row is pending (the local edit wins and stays pending) and returns how many stored docs
 * differed from the local row. The row ids in [rows] are the local rows.
 */
internal class RecordingKind(
    override val kind: String,
    localIds: List<String> = emptyList(),
    val events: MutableList<String> = mutableListOf(),
) : SyncedKind {

    private val local = LinkedHashMap<String, Map<String, Any?>>()
    private val pendingIds = LinkedHashSet<String>()

    /** Uids `forget` was called for, in order. */
    val forgotten = mutableListOf<String>()

    /** Runs first in every call with the call's name (`adopt`, `apply`, `pending`, `acknowledge`, `forget`). */
    var onCall: suspend (String) -> Unit = {}

    init {
        localIds.forEach { edit(it, mapOf("n" to 0L)) }
    }

    /** The ids of the local rows. */
    val rows: List<String> get() = local.keys.toList()

    /** A copy of the local row [id], or null. */
    fun row(id: String): Map<String, Any?>? = local[id]

    /** The ids not yet acknowledged. */
    val pendingRowIds: List<String> get() = pendingIds.toList()

    /** Writes [fields] to the local row [id] as a user edit: the row is pending until acknowledged. */
    fun edit(id: String, fields: Map<String, Any?>) {
        local[id] = fields
        pendingIds += id
    }

    /** This kind's calls named [name] ("adopt", "apply", "pending", "acknowledge", "forget"), in order. */
    fun calls(name: String): List<String> = events.filter { it.startsWith("$name $kind ") }

    override fun pendingIds(uid: String): Flow<Set<String>> = flow { emit(pendingRowIds.toSet()) }

    override suspend fun adopt(uid: String) {
        onCall("adopt")
        events += "adopt $kind $uid"
    }

    override suspend fun pending(uid: String): List<CloudDoc> {
        onCall("pending")
        events += "pending $kind $uid"
        return pendingIds.map { CloudDoc(it, local.getValue(it)) }
    }

    override suspend fun apply(uid: String, docs: List<CloudDoc>): Int {
        onCall("apply")
        events += "apply $kind $uid ${docs.joinToString(",") { it.id }}"
        val taken = docs.filter { it.id !in pendingIds }
        val changed = taken.count { local[it.id] != it.fields }
        taken.forEach { local[it.id] = it.fields }
        return changed
    }

    override suspend fun acknowledge(uid: String, uploaded: List<CloudDoc>) {
        onCall("acknowledge")
        events += "acknowledge $kind $uid ${uploaded.joinToString(",") { it.id }}"
        uploaded.forEach { if (local[it.id] == it.fields) pendingIds -= it.id }
    }

    override suspend fun forget(uid: String) {
        onCall("forget")
        events += "forget $kind $uid"
        forgotten += uid
        pendingIds += local.keys
    }
}
