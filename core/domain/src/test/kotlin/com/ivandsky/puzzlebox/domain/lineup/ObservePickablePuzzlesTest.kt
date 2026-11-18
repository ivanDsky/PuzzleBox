package com.ivandsky.puzzlebox.domain.lineup

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.PublishedPuzzle
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.CommunityFilter
import com.ivandsky.puzzlebox.data.repository.CommunityListResult
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeHiddenPuzzles
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The add sheet's one list: three sources merged, filtered on the device, the community read once per sheet. */
class ObservePickablePuzzlesTest {

    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L
    private val registry = TestGames.registry(listOf(alpha, beta))
    private val identity = FakeIdentityService(initial = Identity("me", "me", null))
    private val shared = FakeSharedPuzzleRepository().apply { clock = { now } }
    private val received = FakeReceivedPuzzleRepository()
    private val published = FakePublishedPuzzleRepository()
    private val completions = FakeCompletionRepository()
    private val hiddenPuzzles = FakeHiddenPuzzles()
    private var listCalls = 0
    private var failList = false
    private var gate: CompletableDeferred<Unit>? = null

    private val counting = object : SharedPuzzleRepository by shared {
        override suspend fun listPublic(filter: CommunityFilter): CommunityListResult {
            listCalls++
            gate?.await()
            if (failList) error("boom")
            return shared.listPublic(filter)
        }
    }

    private val communitySource = CommunitySource(counting, hiddenPuzzles, identity, registry) { now }

    private fun observe() = ObservePickablePuzzles(
        completions, communitySource, FriendSource(received, identity) { now }, OwnSource(published, identity),
    )

    private fun query(
        text: String = "",
        fresh: Set<Freshness> = emptySet(),
        sources: Set<PuzzleSource> = emptySet(),
        games: Set<GameId> = emptySet(),
    ) = PickerQuery(text, fresh, sources, games)

    private fun code(value: String) = checkNotNull(ShareCode.parse(value))

    private suspend fun community(code: String, game: GameId, title: String, author: String = "Cleo", hoursAgo: Long = 1) {
        shared.signedInUid = "cleo"
        shared.publishesToday = 0
        shared.publish(
            SharedPuzzleDoc(
                code = code(code), formatVersion = SharedPuzzleDoc.FORMAT_VERSION, gameId = game.value, variant = "${game.value}/n8",
                shareCodecVersion = 1, payload = byteArrayOf(1), title = title, authorName = author, note = "",
                authorUid = "cleo", authorTimeMs = 1_000L, authorHints = 0, createdAtMs = now - hoursAgo * hour,
                unpublished = false, visibility = SharedPuzzleDoc.Visibility.Public,
            ),
        )
    }

    private suspend fun friend(code: String, game: GameId, title: String, author: String = "Bo", openedHoursAgo: Long = 2) =
        received.upsert(
            ReceivedPuzzle(
                code = code(code), gameId = game, variant = TestGames.variant(game), sharedFormatVersion = 1,
                puzzleBytes = byteArrayOf(TestGames.MAGIC), title = title, authorName = author, note = "", authorTimeMs = 1_000L,
                authorHints = 0, authorUid = "bo", openedAt = Instant.ofEpochMilli(now - openedHoursAgo * hour), unpublished = false,
                postState = PostState.Pending, postAttempts = 0, difficulty = null,
            ),
        )

    private suspend fun own(code: String, game: GameId, title: String, hoursAgo: Long = 3) = published.upsert(
        PublishedPuzzle(
            code = code(code), gameId = game, variant = TestGames.variant(game), title = title, authorTimeMs = 1_000L,
            authorHints = 0, createdAt = Instant.ofEpochMilli(now - hoursAgo * hour), unpublished = false, solvesCount = null,
            solvesCountAt = null,
        ),
    )

    /** The list once the community read has settled. */
    private suspend fun TestScope.result(q: PickerQuery = query()): PickerResult {
        var latest: PickerResult? = null
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { observe().invoke(flowOf(q)).collect { latest = it } }
        advanceUntilIdle()
        job.cancel()
        return checkNotNull(latest)
    }

    @Test
    fun `a query finds a community, a friend's and an own puzzle by title or author`() = runTest {
        community("K7Q2MX", alpha, title = "Crown jewels")
        friend("M3N4P5", beta, title = "Plain", author = "Crowner")
        own("R6S7T8", alpha, title = "My CROWN")
        community("V2W3X4", beta, title = "Other")

        val rows = result(query(text = "crown")).rows

        assertEquals(
            listOf(PuzzleSource.Community, PuzzleSource.Friend, PuzzleSource.Mine),
            rows.map { it.entry.source },
        )
    }

    @Test
    fun `a share code finds its puzzle with or without the dash`() = runTest {
        community("K7Q2MX", alpha, title = "A")
        community("V2W3X4", alpha, title = "B")

        assertEquals(listOf(code("K7Q2MX")), result(query(text = "k7q-2mx")).rows.map { it.code })
        assertEquals(listOf(code("K7Q2MX")), result(query(text = "K7Q2")).rows.map { it.code })
    }

    @Test
    fun `new, from friends and one game together keep only a friend's new puzzle of that game`() = runTest {
        community("K7Q2MX", alpha, title = "Community")
        friend("M3N4P5", alpha, title = "Fresh", openedHoursAgo = 5)
        friend("R6S7T8", beta, title = "Other game")
        friend("V2W3X4", alpha, title = "Solved")
        own("Y5Z6A7", alpha, title = "Mine")
        completions.setRecords(listOf(record(alpha, "V2W3X4")))

        val rows = result(query(fresh = setOf(Freshness.New), sources = setOf(PuzzleSource.Friend), games = setOf(alpha))).rows

        assertEquals(listOf(code("M3N4P5")), rows.map { it.code })
        assertTrue(rows.single().isNew)
    }

    @Test
    fun `rows come newest first and a solved one is marked played`() = runTest {
        community("K7Q2MX", alpha, title = "Community", hoursAgo = 4)
        friend("M3N4P5", alpha, title = "Friend", openedHoursAgo = 2)
        own("R6S7T8", alpha, title = "Own", hoursAgo = 3)
        completions.setRecords(listOf(record(alpha, "R6S7T8")))

        val rows = result().rows

        assertEquals(listOf("Friend", "Own", "Community"), rows.map { it.entry.title })
        assertEquals(listOf(false, true, false), rows.map { it.played })
        assertEquals(listOf("Friend"), result(query(fresh = setOf(Freshness.NotPlayed), sources = setOf(PuzzleSource.Friend))).rows.map { it.entry.title })
    }

    @Test
    fun `refiltering the query never reads the community again`() = runTest {
        community("K7Q2MX", alpha, title = "Crown")
        val queries = MutableStateFlow(query())
        var latest: PickerResult? = null
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { observe().invoke(queries).collect { latest = it } }
        assertEquals(1, latest?.rows?.size)

        queries.value = query(text = "nothing")

        assertEquals(emptyList<PickablePuzzle>(), latest?.rows)
        assertEquals(registry.all.size, listCalls)
    }

    @Test
    fun `a three day old community puzzle is listed but not new`() = runTest {
        shared.clock = { now - 3 * 24 * hour }
        community("K7Q2MX", alpha, title = "Old", hoursAgo = 72)

        val row = result().rows.single()

        assertFalse(row.isNew)
    }

    @Test
    fun `a hidden or own community puzzle is left out`() = runTest {
        community("K7Q2MX", alpha, title = "Hidden")
        community("V2W3X4", alpha, title = "Kept")
        hiddenPuzzles.add(code("K7Q2MX"))

        assertEquals(listOf("Kept"), result().rows.map { it.entry.title })
    }

    @Test
    fun `friends' and own rows show while the community read is still pending`() = runTest {
        friend("M3N4P5", alpha, title = "Friend")
        own("R6S7T8", alpha, title = "Own")
        community("K7Q2MX", alpha, title = "Community")
        gate = CompletableDeferred()
        var latest: PickerResult? = null
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { observe().invoke(flowOf(query())).collect { latest = it } }
        testScheduler.runCurrent()

        assertEquals(listOf("Friend", "Own"), latest?.rows?.map { it.entry.title })

        gate?.complete(Unit)
        testScheduler.runCurrent()

        assertEquals(3, latest?.rows?.size)
    }

    @Test
    fun `a friend's puzzle that is also public shows once, as the friend's`() = runTest {
        community("K7Q2MX", alpha, title = "Same")
        friend("K7Q2MX", alpha, title = "Same")

        assertEquals(listOf(PuzzleSource.Friend), result().rows.map { it.entry.source })
    }

    @Test
    fun `a friend's puzzle opened long ago is not new even unsolved`() = runTest {
        friend("M3N4P5", alpha, title = "Old", openedHoursAgo = 72)
        friend("R6S7T8", alpha, title = "Fresh", openedHoursAgo = 1)

        assertEquals(listOf("Fresh"), result(query(fresh = setOf(Freshness.New))).rows.map { it.entry.title })
    }

    @Test
    fun `offline shows the cached community rows and says so`() = runTest {
        community("K7Q2MX", alpha, title = "Cached")
        assertFalse(result().offline)

        shared.online = false

        val offline = result()
        assertTrue(offline.offline)
        assertEquals(listOf("Cached"), offline.rows.map { it.entry.title })
    }

    @Test
    fun `a read that throws is offline with the rest of the list intact`() = runTest {
        own("R6S7T8", alpha, title = "Own")
        failList = true

        val outcome = result()

        assertTrue(outcome.offline)
        assertEquals(listOf("Own"), outcome.rows.map { it.entry.title })
    }

    private fun record(game: GameId, custom: String) = CompletionRecord(
        puzzleRef = TestGames.Ref(game, PuzzleKey.Custom(custom)),
        time = 30_000L, hints = 0, stats = RecordStats(schemaVersion = 1, blob = "{}"), isRecord = true,
    )
}
