package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.model.PublishedPuzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.repository.FakeCustomBoardRepository
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Published rows' solve counts (spec A20): a count older than ten minutes, or never taken,
 * is fetched again and cached with its instant; a fresher one is left alone; a count that can't
 * be read (offline) keeps the cached value and its instant.
 */
class SyncSolvesCountsTest {

    private val published = FakePublishedPuzzleRepository()
    private val boards = FakeCustomBoardRepository()
    private val sync = SyncSolvesCounts(published, boards, clock = { NOW })

    @Test
    fun `a count checked 11 minutes ago is fetched again`() = runTest {
        published.upsert(row(CODE, solvesCount = 1, at = NOW.minusSeconds(11 * 60)))
        solvedBy(CODE, 3)

        sync()

        assertEquals(3 to NOW, cached(CODE))
    }

    @Test
    fun `a count checked 9 minutes ago is left alone`() = runTest {
        val checked = NOW.minusSeconds(9 * 60)
        published.upsert(row(CODE, solvesCount = 1, at = checked))
        solvedBy(CODE, 3)

        sync()

        assertEquals(1 to checked, cached(CODE))
    }

    @Test
    fun `a never fetched count is fetched, zero included`() = runTest {
        published.upsert(row(CODE, solvesCount = null, at = null))
        published.upsert(row(OTHER, solvesCount = null, at = null))
        solvedBy(CODE, 2)

        sync()

        assertEquals(2 to NOW, cached(CODE))
        assertEquals(0 to NOW, cached(OTHER))
    }

    @Test
    fun `offline keeps the cached count and its instant`() = runTest {
        val checked = NOW.minusSeconds(60 * 60)
        published.upsert(row(CODE, solvesCount = 5, at = checked))
        solvedBy(CODE, 7)
        boards.online = false

        sync()

        assertEquals(5 to checked, cached(CODE))
    }

    @Test
    fun `an unpublished row still has its count refreshed`() = runTest {
        published.upsert(row(CODE, solvesCount = 1, at = null).copy(unpublished = true))
        solvedBy(CODE, 4)

        sync()

        assertEquals(4 to NOW, cached(CODE))
    }

    private suspend fun solvedBy(code: ShareCode, players: Int) = repeat(players) { n ->
        boards.submit(code, LeaderboardEntry("uid-$n", "P$n", "custom/fake/${code.value}", 60_000L + n, 0, NOW.toEpochMilli()))
    }

    private suspend fun cached(code: ShareCode): Pair<Int?, Instant?> =
        checkNotNull(published.observe(code).first()).let { it.solvesCount to it.solvesCountAt }

    private fun row(code: ShareCode, solvesCount: Int?, at: Instant?) = PublishedPuzzle(
        code = code,
        gameId = GAME,
        variant = VARIANT,
        title = "Castle",
        authorTimeMs = 102_000,
        authorHints = 0,
        createdAt = NOW.minusSeconds(86_400),
        unpublished = false,
        solvesCount = solvesCount,
        solvesCountAt = at,
    )

    private companion object {
        val CODE: ShareCode = checkNotNull(ShareCode.parse("K7Q2MX"))
        val OTHER: ShareCode = checkNotNull(ShareCode.parse("B4N8TW"))
        val GAME = GameId("fake")
        val VARIANT = VariantId("small")
        val NOW: Instant = Instant.parse("2026-11-07T10:00:00Z")
    }
}
