package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.firestore.FirebaseFirestoreException
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.PublishResult
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A publish the rules deny is [PublishResult.ClockSkew] only when the device's UTC day, the one
 * stamped on the write, is not the server's day; any other denial stays [PublishResult.Rejected].
 * Robolectric: constructing real Firebase exceptions reaches android.*.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirestoreSharedPuzzleClockSkewTest {

    /** Refuses the quota stamp unless it is [serverDay], as `isPublishQuotaBump` does. */
    private class DayCheckingSharedPuzzleStore(var serverDay: String) : SharedPuzzleStore {
        var otherDenial = false
        var stamped: String? = null

        override suspend fun publish(code: String, fields: Map<String, Any?>, uid: String, utcDay: String): StoreWrite {
            stamped = utcDay
            if (otherDenial || utcDay != serverDay) throw denied()
            return StoreWrite.Created
        }

        override suspend fun unpublish(code: String) = Unit

        override suspend fun get(code: String): Map<String, Any?>? = null
    }

    private val store = DayCheckingSharedPuzzleStore(serverDay = SERVER_DAY)
    private var deviceDay = SERVER_DAY
    private var readableServerDay: String? = SERVER_DAY
    private var serverDayHangs = false
    private var serverDayReads = 0

    private val repository = FirestoreSharedPuzzleRepository(
        store = store,
        currentUid = { AUTHOR },
        utcDay = { deviceDay },
        serverDay = {
            serverDayReads++
            if (serverDayHangs) awaitCancellation()
            readableServerDay
        },
    )

    @Test
    fun `a denial on a device day other than the server day is ClockSkew`() = runTest {
        deviceDay = BEHIND_DAY

        assertEquals(PublishResult.ClockSkew, repository.publish(doc()))
        assertEquals(BEHIND_DAY, store.stamped)
    }

    @Test
    fun `a device day ahead of the server day is ClockSkew too`() = runTest {
        deviceDay = AHEAD_DAY

        assertEquals(PublishResult.ClockSkew, repository.publish(doc()))
    }

    @Test
    fun `a denial on the server's own day stays Rejected`() = runTest {
        store.otherDenial = true

        assertTrue(repository.publish(doc()) is PublishResult.Rejected)
    }

    @Test
    fun `a denial with the server day unreadable stays Rejected`() = runTest {
        deviceDay = BEHIND_DAY
        readableServerDay = null

        assertTrue(repository.publish(doc()) is PublishResult.Rejected)
    }

    @Test
    fun `a server day that never answers is Rejected once the bound passes`() = runTest {
        deviceDay = BEHIND_DAY
        serverDayHangs = true

        assertTrue(repository.publish(doc()) is PublishResult.Rejected)
        assertEquals(SERVER_DAY_TIMEOUT.inWholeMilliseconds, testScheduler.currentTime)
    }

    @Test
    fun `a publish on the server's day is Published and never reads the server day`() = runTest {
        assertEquals(PublishResult.Published(code()), repository.publish(doc()))
        assertEquals(0, serverDayReads)
    }

    private fun code(): ShareCode = checkNotNull(ShareCode.parse("K7Q2MX"))

    private fun doc() = SharedPuzzleDoc(
        code = code(),
        formatVersion = SharedPuzzleDoc.FORMAT_VERSION,
        gameId = "queens",
        variant = "classic",
        shareCodecVersion = 1,
        payload = byteArrayOf(1, 2, 3, 4),
        title = "Corner queens",
        authorName = "Ivan",
        note = "Start in the corner",
        authorUid = AUTHOR,
        authorTimeMs = 102_000L,
        authorHints = 1,
        createdAtMs = 1_700_000_000_000L,
        unpublished = false,
    )

    private companion object {
        const val AUTHOR = "author-uid"
        const val SERVER_DAY = "2026-11-07"
        const val BEHIND_DAY = "2026-11-06"
        const val AHEAD_DAY = "2026-11-08"

        fun denied() = FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
    }
}
