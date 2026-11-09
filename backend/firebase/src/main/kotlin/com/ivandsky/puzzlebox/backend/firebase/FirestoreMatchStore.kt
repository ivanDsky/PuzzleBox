package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.TransactionOptions
import java.time.Instant
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

internal class FirestoreMatchStore : MatchStore {

    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private fun match(code: String) = firestore.collection(MATCHES).document(code)

    private fun quota(uid: String) = firestore.collection(QUOTA).document(uid)

    override suspend fun codeTaken(code: String): Boolean =
        firestore.collection(PUZZLES).document(code).get(Source.SERVER).await().exists()

    override suspend fun matchExists(code: String): Boolean = match(code).get(Source.SERVER).await().exists()

    override suspend fun create(code: String, fields: Map<String, Any?>, uid: String, utcDay: String): MatchCreate {
        val matchDoc = match(code)
        val quotaDoc = quota(uid)
        return rerunOnLostRace {
            firestore.runTransaction<MatchCreate>(SINGLE_ATTEMPT) { tx ->
                val existing = tx.get(matchDoc)
                if (existing.exists()) {
                    return@runTransaction MatchCreate.Exists(existing.getString(MatchDocCodec.FIELD_CHALLENGER_UID).orEmpty())
                }
                val count = nextMatchCount(tx.get(quotaDoc).data, utcDay)
                    ?: return@runTransaction MatchCreate.CapReached
                tx.set(matchDoc, fields)
                tx.set(quotaDoc, authorFields(utcDay, count, code))
                MatchCreate.Created
            }.await()
        }
    }

    override suspend fun claim(code: String, fields: Map<String, Any?>, uid: String, now: Instant): SeatWrite {
        val matchDoc = match(code)
        return rerunOnLostRace {
            firestore.runTransaction<SeatWrite>(SINGLE_ATTEMPT) { tx ->
                settledClaim(tx.get(matchDoc).data, uid, now)
                    ?: SeatWrite.Claimed.also { tx.update(matchDoc, fields) }
            }.await()
        }
    }

    override suspend fun cancel(code: String, uid: String): SeatWrite =
        flag(match(code), MatchDocCodec.FIELD_CANCELLED) { settledCancel(it, uid) }

    override suspend fun decline(code: String, uid: String): SeatWrite =
        flag(match(code), MatchDocCodec.FIELD_DECLINED) { settledDecline(it, uid) }

    /** Sets [field] to true unless [settled] answers from what the transaction read. */
    private suspend fun flag(
        doc: DocumentReference,
        field: String,
        settled: (Map<String, Any?>?) -> SeatWrite?,
    ): SeatWrite = rerunOnLostRace {
        firestore.runTransaction<SeatWrite>(SINGLE_ATTEMPT) { tx ->
            settled(tx.get(doc).data) ?: SeatWrite.Done.also { tx.update(doc, field, true) }
        }.await()
    }

    override fun observe(code: String): Flow<MatchSnapshot> = callbackFlow {
        val registration = match(code).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) trySend(snapshot.toMatchSnapshot())
        }
        awaitClose { registration.remove() }
    }

    override fun observeMine(uid: String): Flow<Map<String, Map<String, Any?>>> = callbackFlow {
        val registration = firestore.collection(MATCHES)
            .whereArrayContains(MatchDocCodec.FIELD_PARTICIPANTS, uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents.orEmpty().associate { it.id to it.estimatedData() })
            }
        awaitClose { registration.remove() }
    }

    override suspend fun post(code: String, fields: Map<String, Any?>, uid: String, legIndex: Int) {
        val (progressField, progressValue) = MatchDocCodec.progressUpdate(uid, legIndex)
        firestore.batch()
            .set(result(code, legIndex, uid), fields)
            .update(match(code), progressField, progressValue)
            .commit()
            .await()
    }

    // From the server: a cached copy would answer a read the rules have not judged.
    override suspend fun readResult(code: String, legIndex: Int, uid: String): Map<String, Any?>? =
        result(code, legIndex, uid).get(Source.SERVER).await().data

    private fun result(code: String, legIndex: Int, uid: String) =
        match(code).collection(RESULTS).document(MatchDocCodec.resultId(legIndex, uid))

    // A pending local write has no server timestamp yet; ESTIMATE keeps `createdAt` decodable.
    private fun DocumentSnapshot.estimatedData(): Map<String, Any?> = getData(ServerTimestampBehavior.ESTIMATE).orEmpty()

    private fun DocumentSnapshot.toMatchSnapshot(): MatchSnapshot = when {
        exists() -> MatchSnapshot.Doc(estimatedData())
        metadata.isFromCache -> MatchSnapshot.Unconfirmed
        else -> MatchSnapshot.Absent
    }

    private companion object {
        const val PUZZLES = "puzzles"
        const val MATCHES = "matches"
        const val RESULTS = "results"
        const val QUOTA = "matchQuota"

        // The SDK retries UNAVAILABLE with backoff; one attempt makes offline fail at once.
        val SINGLE_ATTEMPT: TransactionOptions = TransactionOptions.Builder().setMaxAttempts(1).build()
    }
}
