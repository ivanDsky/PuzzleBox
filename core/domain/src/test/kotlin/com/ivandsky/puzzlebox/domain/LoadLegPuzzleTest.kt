package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.PublishedPuzzle
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.DailyDay
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The puzzle of a duel leg: cached or fetched for a daily leg; stored or opened for a friend's; a failed read told from a settled no. */
class LoadLegPuzzleTest {

    private val game = GameId("Zip")
    private val day = LocalDate.of(2026, 11, 9)
    private val puzzle = Puzzle(game, VariantId("v1"), 1, byteArrayOf(7))
    private val daily = FakeDailyRepository()
    private val received = FakeReceivedPuzzleRepository()
    private val shared = FakeSharedPuzzleRepository()
    private val published = FakePublishedPuzzleRepository()
    private val loadShared = LoadSharedPuzzle(
        registry = GameRegistry(listOf(withEditor(PairEditor.GAME))),
        shared = shared,
        received = received,
        budget = { Budget.within(1_000, System::nanoTime) },
        clock = { Instant.EPOCH },
    )
    private val load = LoadLegPuzzle(daily = daily, received = received, loadShared = loadShared, published = published)

    private fun found(read: LegPuzzleRead) = (read as LegPuzzleRead.Found).puzzle

    @Test
    fun `a cached daily leg answers from the cache without a fetch`() = runTest {
        daily.setCached(game, day, puzzle)

        assertEquals(puzzle, found(load(LegRef.Daily(game, day, 1))))
        assertEquals(0, daily.dayCalls(game, day))
    }

    @Test
    fun `an uncached daily leg is fetched`() = runTest {
        daily.setDay(game, day, DailyDay.Ready(puzzle, 1))

        assertEquals(puzzle, found(load(LegRef.Daily(game, day, 1))))
        assertEquals(1, daily.dayCalls(game, day))
    }

    @Test
    fun `a daily leg that cannot be fetched is unavailable, one that is not out is absent`() = runTest {
        daily.setDay(game, day, DailyDay.Failed(FetchError.Unavailable("offline")))
        assertEquals(LegPuzzleRead.Unavailable, load(LegRef.Daily(game, day, 1)))

        daily.setDay(game, day, DailyDay.Failed(FetchError.NotFound("404")))
        assertEquals(LegPuzzleRead.Absent, load(LegRef.Daily(game, day, 1)))
    }

    @Test
    fun `a friend's leg that is neither stored nor on the server is absent`() = runTest {
        assertEquals(LegPuzzleRead.Absent, load(LegRef.Custom(game, checkNotNull(ShareCode.parse("ABC234")))))
    }

    private val code = checkNotNull(ShareCode.parse("K7Q2MX"))
    private val custom = LegRef.Custom(game, code)
    private val pairLeg = LegRef.Custom(PairEditor.GAME, code)

    private suspend fun publishedThenTakenDown(game: GameId = this.game, payload: ByteArray = byteArrayOf(1)) {
        shared.signedInUid = "cleo"
        shared.publish(
            SharedPuzzleDoc(
                code = code, formatVersion = SharedPuzzleDoc.FORMAT_VERSION, gameId = game.value, variant = if (game == PairEditor.GAME) PairEditor.VARIANT.value else "v1",
                shareCodecVersion = 1, payload = payload, title = "Crown", authorName = "Cleo", note = "",
                authorUid = "cleo", authorTimeMs = 1_000L, authorHints = 0, createdAtMs = 0L, unpublished = false,
            ),
        )
        shared.unpublish(code)
    }

    private suspend fun open(game: GameId = this.game, payload: ByteArray = byteArrayOf(1)) {
        shared.signedInUid = "cleo"
        shared.publish(
            SharedPuzzleDoc(
                code = code, formatVersion = SharedPuzzleDoc.FORMAT_VERSION, gameId = game.value, variant = PairEditor.VARIANT.value,
                shareCodecVersion = PairEditor.SHARE_VERSION, payload = payload, title = "Crown", authorName = "Cleo", note = "",
                authorUid = "cleo", authorTimeMs = 1_000L, authorHints = 0, createdAtMs = 0L, unpublished = false,
            ),
        )
    }

    private suspend fun ownPublished(unpublished: Boolean) = published.upsert(
        PublishedPuzzle(
            code = code, gameId = PairEditor.GAME, variant = PairEditor.VARIANT, title = "Crown", authorTimeMs = 1_000L,
            authorHints = 0, createdAt = Instant.EPOCH, unpublished = unpublished, solvesCount = null, solvesCountAt = null,
        ),
    )

    @Test
    fun `a friend's leg its author took down, never saved here, is taken down`() = runTest {
        publishedThenTakenDown()

        assertEquals(LegPuzzleRead.TakenDown, load(custom))
    }

    @Test
    fun `a friend's leg taken down after this device saved it is still found`() = runTest {
        publishedThenTakenDown()
        received.upsert(
            ReceivedPuzzle(
                code = code, gameId = game, variant = VariantId("v1"), sharedFormatVersion = 1, puzzleBytes = byteArrayOf(7),
                title = "Crown", authorName = "Cleo", note = "", authorTimeMs = 1_000L, authorHints = 0, authorUid = "cleo",
                openedAt = Instant.EPOCH, unpublished = false, postState = PostState.Pending, postAttempts = 0, difficulty = null,
            ),
        )

        assertEquals(puzzle, found(load(custom)))
    }

    @Test
    fun `a friend's leg read while offline is unavailable, never taken down`() = runTest {
        publishedThenTakenDown()
        shared.online = false

        assertEquals(LegPuzzleRead.Unavailable, load(custom))
    }

    @Test
    fun `a friend's leg is read without being saved here until the player accepts or plays it`() = runTest {
        open(PairEditor.GAME, byteArrayOf(1, 0))

        val read = load(pairLeg)

        assertEquals("the puzzle the viewer sees", PairEditor.GAME, found(read).gameId)
        assertNull("viewing keeps nothing", received.get(code))
        loadShared(code)
        assertEquals("accepting or playing keeps it", "Crown", received.get(code)?.title)
    }

    @Test
    fun `the player's own unpublished puzzle is shown, not taken down, and not saved as a friend's`() = runTest {
        open(PairEditor.GAME, byteArrayOf(1, 0))
        shared.unpublish(code)
        ownPublished(unpublished = true)

        assertEquals(PairEditor.GAME, found(load(pairLeg)).gameId)
        assertNull(received.get(code))
    }

    @Test
    fun `an own unpublished puzzle that cannot be read is never called taken down`() = runTest {
        publishedThenTakenDown(PairEditor.GAME, byteArrayOf(1, 2, 3))
        ownPublished(unpublished = true)

        val read = load(pairLeg)

        assertNotEquals(LegPuzzleRead.TakenDown, read)
        assertNull(received.get(code))
    }
}
