package com.ivandsky.puzzlebox.domain.random

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.domain.ListCommunityPuzzles
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.repository.FakeHiddenPuzzles
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** New today over the fake community list: pool filters, the last pick, and each list failure. */
class LoadRandomSharedTest {
    private val shared = FakeSharedPuzzleRepository().apply { clock = { NOW } }
    private val identity = FakeIdentityService(Identity(uid = ME, displayName = "Me", photoUrl = null))
    private val list = ListCommunityPuzzles(shared, FakeHiddenPuzzles(), identity) { NOW }
    private val seeds = 1..200
    private val zip = GameId("zip")
    private val tango = GameId("tango")

    private suspend fun publish(code: String, gameId: String = "zip", difficulty: Difficulty.Level? = null) {
        shared.signedInUid = OTHER
        shared.publishesToday = 0
        shared.publish(
            SharedPuzzleDoc(
                code = code(code), formatVersion = SharedPuzzleDoc.FORMAT_VERSION, gameId = gameId, variant = "$gameId/v",
                shareCodecVersion = 1, payload = byteArrayOf(1), title = "T$code", authorName = "A", note = "",
                authorUid = OTHER, authorTimeMs = 1_000L, authorHints = 0, createdAtMs = NOW - HOUR, unpublished = false,
                visibility = SharedPuzzleDoc.Visibility.Public, difficulty = difficulty,
            ),
        )
    }

    private suspend fun pick(
        seed: Int,
        game: GameId? = null,
        difficulties: Set<Difficulty.Level> = emptySet(),
        last: RandomPick? = null,
    ) = LoadRandomShared(list, Random(seed))(RandomFilter.Community(game, difficulties), last)

    private fun picked(code: String) = RandomOutcome.Picked(RandomPick.Shared(code(code)))

    @Test
    fun `a pick is always a listed puzzle of the chosen game and difficulty`() = runTest {
        publish("AAA111", "zip", Difficulty.Level.Easy)
        publish("BBB222", "zip", Difficulty.Level.Hard)
        publish("CCC333", "tango", Difficulty.Level.Easy)
        publish("DDD444", "zip", Difficulty.Level.Easy)
        val seen = mutableSetOf<RandomOutcome>()
        for (seed in seeds) {
            seen += pick(seed, zip, setOf(Difficulty.Level.Easy))
            assertTrue(pick(seed, tango) == picked("CCC333"))
        }
        assertEquals(setOf(picked("AAA111"), picked("DDD444")), seen)
    }

    @Test
    fun `any game and any difficulty draws from the whole list`() = runTest {
        publish("AAA111", "zip")
        publish("CCC333", "tango")
        assertEquals(setOf(picked("AAA111"), picked("CCC333")), seeds.map { pick(it) }.toSet())
    }

    @Test
    fun `the last pick is skipped while another puzzle is listed`() = runTest {
        publish("AAA111")
        publish("BBB222")
        publish("CCC333")
        val last = RandomPick.Shared(code("BBB222"))
        val seen = seeds.map { pick(it, last = last) }.toSet()
        assertEquals(setOf(picked("AAA111"), picked("CCC333")), seen)
    }

    @Test
    fun `a list of exactly the last pick repeats it`() = runTest {
        publish("AAA111")
        assertEquals(picked("AAA111"), pick(1, last = RandomPick.Shared(code("AAA111"))))
    }

    @Test
    fun `a last pick that was a past day does not hide a puzzle`() = runTest {
        publish("AAA111")
        assertEquals(picked("AAA111"), pick(1, last = RandomPick.Day(zip, java.time.LocalDate.of(2026, 11, 17))))
    }

    @Test
    fun `an empty list is nothing left`() = runTest {
        assertEquals(RandomOutcome.NothingLeft, pick(1))
        publish("AAA111", "zip")
        assertEquals(RandomOutcome.NothingLeft, pick(1, game = tango))
    }

    @Test
    fun `offline and refused lists say so`() = runTest {
        publish("AAA111")
        shared.online = false
        assertEquals(RandomOutcome.Offline, pick(1))
        shared.online = true
        shared.listRefused = true
        assertEquals(RandomOutcome.Refused, pick(1))
    }

    private fun code(value: String): ShareCode = checkNotNull(ShareCode.parse(value))

    private companion object {
        const val ME = "me"
        const val OTHER = "other"
        const val NOW = 1_800_000_000_000L
        const val HOUR = 60L * 60 * 1000
    }
}
