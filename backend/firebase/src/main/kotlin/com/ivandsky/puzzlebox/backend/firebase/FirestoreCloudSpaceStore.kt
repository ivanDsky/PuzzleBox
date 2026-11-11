package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.ivandsky.puzzlebox.data.cloud.CloudDoc
import kotlinx.coroutines.tasks.await

/**
 * [CloudSpaceStore] over `users/{uid}/{kind}/{docId}`. Writes are set-merges, so nested maps merge and any other value
 * replaces; each stamps `updatedAt` with the server time, which the rules require and the delta-sync cursor reads back.
 * Reads come from the server, never the cache. `Blob` and `Timestamp` become `ByteArray` and epoch milliseconds.
 */
internal class FirestoreCloudSpaceStore : CloudSpaceStore {

    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private fun space(uid: String, kind: String) = firestore.collection(USERS).document(uid).collection(kind)

    override suspend fun read(uid: String, kind: String, cursorMs: Long?): List<CloudDoc> {
        val collection = space(uid, kind)
        val query: Query = if (cursorMs == null) collection else collection.whereGreaterThanOrEqualTo(UPDATED_AT, timestampOf(cursorMs))
        return query.get(Source.SERVER).await().documents.map { it.toCloudDoc() }
    }

    override suspend fun ids(uid: String, kind: String): List<String> =
        space(uid, kind).get(Source.SERVER).await().documents.map { it.id }

    override suspend fun commitPut(uid: String, kind: String, docs: List<CloudDoc>) {
        require(docs.size <= CLOUD_BATCH_LIMIT) { "a batch holds at most $CLOUD_BATCH_LIMIT writes" }
        val collection = space(uid, kind)
        val batch = firestore.batch()
        docs.forEach { doc ->
            val data = toFirestoreMap(doc.fields) + (UPDATED_AT to FieldValue.serverTimestamp())
            batch.set(collection.document(doc.id), data, SetOptions.merge())
        }
        batch.commit().await()
    }

    override suspend fun commitDelete(uid: String, kind: String, ids: List<String>) {
        require(ids.size <= CLOUD_BATCH_LIMIT) { "a batch holds at most $CLOUD_BATCH_LIMIT writes" }
        val collection = space(uid, kind)
        val batch = firestore.batch()
        ids.forEach { batch.delete(collection.document(it)) }
        batch.commit().await()
    }

    private fun DocumentSnapshot.toCloudDoc(): CloudDoc {
        val data = checkNotNull(data) { "$id has no data" }
        val fields = (data - UPDATED_AT).mapValues { toPlainValue(it.value) }
        return CloudDoc(id, fields, getTimestamp(UPDATED_AT)?.let(::epochMsOf))
    }

    private companion object {
        const val USERS = "users"
        const val UPDATED_AT = "updatedAt"
    }
}

/** [fields] with each `ByteArray` as a `Blob`; anything that is not a plain cloud value is refused. */
internal fun toFirestoreMap(fields: Map<String, Any?>): Map<String, Any?> = fields.mapValues { toFirestoreValue(it.value) }

private fun toFirestoreValue(value: Any?): Any? = when (value) {
    null, is String, is Long, is Boolean, is Double -> value
    is ByteArray -> Blob.fromBytes(value)
    is List<*> -> value.map(::toFirestoreValue)
    is Map<*, *> -> value.entries.associate { (key, nested) ->
        require(key is String) { "map keys must be String" }
        key to toFirestoreValue(nested)
    }

    else -> throw IllegalArgumentException("not a plain cloud value: ${value::class.simpleName}")
}

/** A stored value as a plain cloud value: `Blob` is `ByteArray`, `Timestamp` is epoch milliseconds. */
internal fun toPlainValue(value: Any?): Any? = when (value) {
    null, is String, is Long, is Boolean, is Double -> value
    is Blob -> value.toBytes()
    is Timestamp -> epochMsOf(value)
    is List<*> -> value.map(::toPlainValue)
    is Map<*, *> -> value.entries.associate { (key, nested) -> key as String to toPlainValue(nested) }
    else -> throw IllegalStateException("not a plain cloud value: ${value::class.simpleName}")
}

/** The instant [ms] after the epoch; Firestore keeps microseconds, so a doc stamped within this millisecond is `>=` it. */
internal fun timestampOf(ms: Long): Timestamp = Timestamp(Math.floorDiv(ms, MS_PER_SECOND), Math.floorMod(ms, MS_PER_SECOND).toInt() * NS_PER_MS)

/** [timestamp] in whole epoch milliseconds, rounded down. */
internal fun epochMsOf(timestamp: Timestamp): Long = timestamp.seconds * MS_PER_SECOND + timestamp.nanoseconds / NS_PER_MS

private const val MS_PER_SECOND = 1_000L
private const val NS_PER_MS = 1_000_000
