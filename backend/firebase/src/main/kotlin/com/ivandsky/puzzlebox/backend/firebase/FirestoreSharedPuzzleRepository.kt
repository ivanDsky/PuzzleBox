package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.PublishResult
import com.ivandsky.puzzlebox.data.repository.SharedFetchResult
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.UnpublishResult
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.coroutines.cancellation.CancellationException

/**
 * Firestore implementation of the shared-puzzle seam over `puzzles/{code}` and `authors/{uid}`
 * (ADR-0020). Publish is create-if-absent, so a retry with the same code and content after a lost
 * ack is [PublishResult.Published]; the same code with other content is [PublishResult.CodeTaken]. Every Firebase exception is mapped to a typed result here.
 */
internal class FirestoreSharedPuzzleRepository(
    private val store: SharedPuzzleStore,
    private val currentUid: () -> String?,
    private val utcDay: () -> String = { LocalDate.now(ZoneOffset.UTC).toString() },
) : SharedPuzzleRepository {

    override suspend fun publish(doc: SharedPuzzleDoc): PublishResult {
        val uid = currentUid() ?: return PublishResult.SignedOut
        if (doc.authorUid != uid) return PublishResult.Rejected("authorUid is not the signed-in user")
        return try {
            val fields = SharedPuzzleDocCodec.encode(doc)
            when (val write = store.publish(doc.code.value, fields, uid, utcDay())) {
                StoreWrite.Created -> PublishResult.Published(doc.code)
                // A retry after a lost ack finds its own doc; anything else the author changed
                // since would leave friends on the old content, so that code counts as taken.
                is StoreWrite.Exists ->
                    if (write.authorUid == uid && SharedPuzzleDocCodec.sameContent(fields, write.fields)) {
                        PublishResult.Published(doc.code)
                    } else {
                        PublishResult.CodeTaken
                    }
                // The transaction read today's count at the cap, so it never sent the create.
                StoreWrite.CapReached -> PublishResult.DailyCapReached
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.isTransient()) PublishResult.Offline else PublishResult.Rejected(e.message)
        }
    }

    override suspend fun unpublish(code: ShareCode): UnpublishResult {
        if (currentUid() == null) return UnpublishResult.Rejected("signed out")
        return try {
            store.unpublish(code.value)
            UnpublishResult.Done
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.isTransient()) UnpublishResult.Offline else UnpublishResult.Rejected(e.message)
        }
    }

    override suspend fun fetch(code: ShareCode): SharedFetchResult {
        val data = try {
            store.get(code.value)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Reads are public, so any failure is the network or the server: worth a retry.
            return SharedFetchResult.Offline
        } ?: return SharedFetchResult.NotFound
        return SharedPuzzleDocCodec.decode(code, data)
    }
}

/** Failures a retry can fix; the rest (denied, malformed, missing) are final. */
private fun Exception.isTransient(): Boolean = when (this) {
    is FirebaseNetworkException -> true
    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.ABORTED,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
        FirebaseFirestoreException.Code.INTERNAL,
        -> true

        else -> false
    }

    else -> false
}
