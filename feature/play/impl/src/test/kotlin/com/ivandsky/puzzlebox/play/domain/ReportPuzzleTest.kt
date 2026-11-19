package com.ivandsky.puzzlebox.play.domain

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.CommunityFilter
import com.ivandsky.puzzlebox.data.repository.CommunityListResult
import com.ivandsky.puzzlebox.data.repository.ReportResult
import com.ivandsky.puzzlebox.data.repository.SharedFetchResult
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.repository.FakeHiddenPuzzles
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reporting hides the code on this device exactly when the server recorded the report or already had it. */
class ReportPuzzleTest {

    private val shared = FakeSharedPuzzleRepository().apply { clock = { NOW } }
    private val hidden = FakeHiddenPuzzles()
    private val report = ReportPuzzle(shared, hidden)

    private suspend fun publishAs(author: String) {
        shared.signedInUid = author
        shared.publish(
            SharedPuzzleDoc(
                code = CODE, formatVersion = SharedPuzzleDoc.FORMAT_VERSION, gameId = "zip", variant = "zip/v",
                shareCodecVersion = 1, payload = byteArrayOf(1), title = "T", authorName = "A", note = "",
                authorUid = author, authorTimeMs = 1_000L, authorHints = 0, createdAtMs = NOW - 1_000L,
                unpublished = false, visibility = SharedPuzzleDoc.Visibility.Public,
            ),
        )
    }

    private suspend fun signedInAsReader() {
        publishAs(AUTHOR)
        shared.signedInUid = READER
    }

    @Test
    fun `a reported puzzle is hidden`() = runTest {
        signedInAsReader()

        assertEquals(ReportResult.Reported, report(CODE))

        assertEquals(setOf(CODE), hidden.hidden)
    }

    @Test
    fun `a second report is already reported and still hides the code`() = runTest {
        signedInAsReader()
        shared.report(CODE)

        assertEquals(ReportResult.AlreadyReported, report(CODE))

        assertEquals(setOf(CODE), hidden.hidden)
    }

    @Test
    fun `the author's own puzzle and a missing code answer already reported and hide`() = runTest {
        publishAs(AUTHOR)

        assertEquals(ReportResult.AlreadyReported, report(CODE))
        assertEquals(ReportResult.AlreadyReported, report(OTHER_CODE))

        assertEquals(setOf(CODE, OTHER_CODE), hidden.hidden)
    }

    @Test
    fun `signed out hides nothing and asks for sign in even with the network down`() = runTest {
        publishAs(AUTHOR)
        shared.signedInUid = null
        shared.online = false

        assertEquals(ReportResult.SignedOut, report(CODE))

        assertTrue(hidden.hidden.isEmpty())
    }

    @Test
    fun `offline hides nothing and a retry can hide`() = runTest {
        signedInAsReader()
        shared.online = false

        assertEquals(ReportResult.Offline, report(CODE))
        assertTrue(hidden.hidden.isEmpty())

        shared.online = true
        assertEquals(ReportResult.Reported, report(CODE))
        assertEquals(setOf(CODE), hidden.hidden)
    }

    @Test
    fun `a rejected report hides nothing`() = runTest {
        signedInAsReader()
        val rejecting = object : SharedPuzzleRepository by shared {
            override suspend fun report(code: ShareCode) = ReportResult.Rejected("boom")
        }

        assertEquals(ReportResult.Rejected("boom"), ReportPuzzle(rejecting, hidden)(CODE))

        assertTrue(hidden.hidden.isEmpty())
    }

    @Test
    fun `a reported puzzle stays out of the list a fresh use case builds`() = runTest {
        signedInAsReader()
        report(CODE)

        // A new use case over the same persisted hide set stands in for a relaunch.
        val identity = FakeIdentityService(Identity(uid = READER, displayName = "R", photoUrl = null))
        val list = ListCommunityPuzzles(shared, hidden, identity) { NOW }

        assertEquals(CommunityListResult.Listed(emptyList()), list(CommunityFilter()))
    }

    @Test
    fun `a hidden code still resolves when typed`() = runTest {
        signedInAsReader()
        report(CODE)

        // Opening by code goes through the repository's fetch; the hide set is not part of that path.
        val fetched = shared.fetch(CODE)

        assertEquals(CODE, (fetched as SharedFetchResult.Found).doc.code)
    }

    private companion object {
        const val AUTHOR = "author"
        const val READER = "reader"
        const val NOW = 1_800_000_000_000L
        val CODE = checkNotNull(ShareCode.parse("K7Q2MX"))
        val OTHER_CODE = checkNotNull(ShareCode.parse("ABC234"))
    }
}
