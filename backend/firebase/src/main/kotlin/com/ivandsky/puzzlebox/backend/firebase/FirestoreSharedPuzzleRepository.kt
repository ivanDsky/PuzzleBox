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
 * ack is [PublishResult.Published]; the same code with other content, or one the author has since
 * unpublished, is [PublishResult.CodeTaken]. Every Firebase exception is mapped to a typed result
 * here. The rules stamp the quota with the server's UTC day, so a denied write from a device on
 * another day is [PublishResult.ClockSkew] rather than [PublishResult.Rejected].
 */
internal class FirestoreSharedPuzzleRepository(
    private val store: SharedPuzzleStore,
    private val currentUid: () -> String?,
    private val utcDay: () -> String = { LocalDate.now(ZoneOffset.UTC).toString() },
    private val serverDay: suspend () -> String? = ::firebaseServerDay,
) : SharedPuzzleRepository {

    override suspend fun publish(doc: SharedPuzzleDoc): PublishResult {
        val uid = currentUid() ?: return PublishResult.SignedOut
        if (doc.authorUid != uid) return PublishResult.Rejected("authorUid is not the signed-in user")
        val day = utcDay()
        return try {
            val fields = SharedPuzzleDocCodec.encode(doc)
            when (val write = store.publish(doc.code.value, fields, uid, day)) {
                StoreWrite.Created -> PublishResult.Published(doc.code)
                // A retry after a lost ack finds its own doc. Changed content would leave friends
                // on the old one and an unpublished doc is closed to them: both count as taken.
                is StoreWrite.Exists -> {
                    val own = write.authorUid == uid &&
                        write.fields[SharedPuzzleDocCodec.FIELD_UNPUBLISHED] != true
                    val retry = own && SharedPuzzleDocCodec.sameContent(fields, write.fields)
                    // The author's lost-reply doc is online under a code only their draft held.
                    if (own && !retry) retire(doc.code)
                    if (retry) PublishResult.Published(doc.code) else PublishResult.CodeTaken
                }
                // The transaction read today's count at the cap, so it never sent the create.
                StoreWrite.CapReached -> PublishResult.DailyCapReached
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            when {
                e.isTransient() -> PublishResult.Offline
                e.isDenied() && deviceDayIsWrong(day) -> PublishResult.ClockSkew
                else -> PublishResult.Rejected(e.message)
            }
        }
    }

    /** True only when the server's day was read and differs from [day]; unreadable means unknown. */
    private suspend fun deviceDayIsWrong(day: String): Boolean = try {
        serverDay()?.let { it != day } ?: false
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    /** Best effort: a failed write leaves the orphan, and never blocks the author's publish. */
    private suspend fun retire(code: ShareCode) {
        try {
            store.unpublish(code.value)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
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

private fun Exception.isDenied(): Boolean =
    this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED
