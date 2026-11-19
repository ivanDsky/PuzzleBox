package com.ivandsky.puzzlebox.play.domain

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.COMMUNITY_WINDOW_MS
import com.ivandsky.puzzlebox.data.repository.CommunityFilter
import com.ivandsky.puzzlebox.data.repository.CommunityListResult
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.repository.FakeHiddenPuzzles
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** New today: the repository's list minus own, hidden and aged-out puzzles, over the fake repository and hide set. */
class ListCommunityPuzzlesTest {

    private val shared = FakeSharedPuzzleRepository().apply { clock = { NOW } }
    private val hidden = FakeHiddenPuzzles()
    private val identity = FakeIdentityService(Identity(uid = ME, displayName = "Me", photoUrl = null))

    private fun listCommunity(clock: () -> Long = { NOW }) = ListCommunityPuzzles(shared, hidden, identity, clock)

    private suspend fun listed(
        filter: CommunityFilter = CommunityFilter(),
        useCase: ListCommunityPuzzles = listCommunity(),
    ): List<ShareCode> = (useCase(filter) as CommunityListResult.Listed).puzzles.map { it.code }

    private suspend fun publish(code: String, author: String, createdAtMs: Long, gameId: String = "zip") {
        shared.signedInUid = author
        shared.publishesToday = 0
        shared.publish(
            SharedPuzzleDoc(
                code = code(code), formatVersion = SharedPuzzleDoc.FORMAT_VERSION, gameId = gameId, variant = "$gameId/v",
                shareCodecVersion = 1, payload = byteArrayOf(1), title = "T$code", authorName = "A", note = "",
                authorUid = author, authorTimeMs = 1_000L, authorHints = 0, createdAtMs = createdAtMs, unpublished = false,
                visibility = SharedPuzzleDoc.Visibility.Public,
            ),
        )
    }

    @Test
    fun `a player sees only other players' visible puzzles, newest first`() = runTest {
        publish("AAA111", OTHER, NOW - HOUR)
        publish("BBB222", ME, NOW - 2 * HOUR)
        publish("CCC333", OTHER, NOW - 3 * HOUR)
        publish("DDD444", OTHER, NOW - 4 * HOUR)
        hidden.add(code("CCC333"))

        assertEquals(listOf(code("AAA111"), code("DDD444")), listed())
    }

    @Test
    fun `a puzzle the device clock puts outside the window is dropped even when the repository listed it`() = runTest {
        // The repository measured its window on an older clock, as a list left on screen would have been.
        shared.clock = { NOW - 3 * HOUR }
        publish("AAA111", OTHER, NOW - HOUR)
        publish("BBB222", OTHER, NOW - 25 * HOUR)

        assertEquals(listOf(code("AAA111")), listed())
    }

    @Test
    fun `a puzzle exactly a day old is kept and one a millisecond older is not`() = runTest {
        shared.clock = { NOW - 3 * HOUR }
        publish("AAA111", OTHER, NOW - COMMUNITY_WINDOW_MS)
        publish("BBB222", OTHER, NOW - COMMUNITY_WINDOW_MS - 1)

        assertEquals(listOf(code("AAA111")), listed())
    }

    @Test
    fun `the window moves with the clock the use case is given`() = runTest {
        publish("AAA111", OTHER, NOW - HOUR)

        assertEquals(emptyList<ShareCode>(), listed(useCase = listCommunity(clock = { NOW + COMMUNITY_WINDOW_MS })))
    }

    @Test
    fun `signed out nothing is own and the rest is listed`() = runTest {
        publish("AAA111", OTHER, NOW - HOUR)
        publish("BBB222", ME, NOW - 2 * HOUR)
        identity.currentIdentity = null

        assertEquals(listOf(code("AAA111"), code("BBB222")), listed())
    }

    @Test
    fun `the filter reaches the repository`() = runTest {
        publish("AAA111", OTHER, NOW - HOUR, gameId = "zip")
        publish("BBB222", OTHER, NOW - 2 * HOUR, gameId = "tango")

        assertEquals(listOf(code("BBB222")), listed(CommunityFilter(gameId = "tango")))
    }

    @Test
    fun `an empty list stays an empty list`() = runTest {
        assertEquals(CommunityListResult.Listed(emptyList()), listCommunity()(CommunityFilter()))
    }

    @Test
    fun `offline and refused pass through unchanged`() = runTest {
        publish("AAA111", OTHER, NOW - HOUR)

        shared.online = false
        assertEquals(CommunityListResult.Offline, listCommunity()(CommunityFilter()))

        shared.online = true
        shared.listRefused = true
        assertEquals(CommunityListResult.Refused, listCommunity()(CommunityFilter()))
    }

    private companion object {
        const val ME = "me"
        const val OTHER = "other"
        const val NOW = 1_800_000_000_000L
        const val HOUR = 60L * 60 * 1000

        fun code(value: String) = checkNotNull(ShareCode.parse(value))
    }
}
