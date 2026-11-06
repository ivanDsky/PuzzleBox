package com.ivandsky.puzzlebox.game

import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The by-number edition contract (ADR-0017) over inline bodies in both games-by-id wire
 * forms LinkedIn answers with: the normalized `included[]` form and the plain
 * `data.identityDashGamesById` form. Real per-game fixtures live in each game's tests.
 */
class LinkedInEditionTest {

    private val zip = LinkedInEdition(gameTypeId = 6, day1 = LocalDate.of(2025, 3, 18), path = "games/zip/", objectName = "trailGamePuzzle")

    private fun normalized(urnGame: Int, urnNumber: Int, puzzle: String = """{"trailGamePuzzle":{"gridSize":6}}""") =
        """{"data":{"data":{"*identityDashGamesById":"urn:li:fsd_game:($GUEST,$urnGame,$urnNumber)"}},""" +
            """"included":[{"gameTypeId":$urnGame,"puzzleId":598,"gamePuzzle":$puzzle,""" +
            """"entityUrn":"urn:li:fsd_game:($GUEST,$urnGame,$urnNumber)"}]}"""

    private fun plain(urnNumber: Int) =
        """{"data":{"identityDashGamesById":{"gameTypeId":6,"puzzleId":598,""" +
            """"gamePuzzle":{"trailGamePuzzle":{"gridSize":7}},"entityUrn":"urn:li:fsd_game:($GUEST,6,$urnNumber)"}}}"""

    @Test
    fun `number counts days from day1, which is number 1`() {
        assertEquals(1, zip.number(LocalDate.of(2025, 3, 18)))
        assertEquals(592, zip.number(LocalDate.of(2026, 10, 30)))
        assertEquals(598, zip.number(LocalDate.of(2026, 11, 5)))
    }

    @Test
    fun `request names the game path and asks games-by-id for the percent-encoded urn`() {
        val request = zip.request(592)

        assertEquals("games/zip/", request.path)
        assertEquals("trailGamePuzzle", request.objectName)
        assertEquals("voyagerIdentityDashGames.f6c16c0fa3fc425ade72fdb9d4e2e133", request.persistedQuery?.queryId)
        assertEquals(
            "(gameUrn:urn%3Ali%3Afsd_game%3A%28ACoAAAAAAAABWzs7tPEzjbzdTu0bZDhDZpin_Ds%2C6%2C592%29)",
            request.persistedQuery?.variables,
        )
        assertNull(request.graphqlBody)
        assertFalse(request.scanPageShell)
    }

    @Test
    fun `a by-id request skips the stream, which only ever carries today's puzzle`() {
        assertFalse(zip.request(592).stream)
    }

    @Test
    fun `request accepts any by-id body of its game, whatever the number`() {
        val accepts = zip.request(592).accepts

        assertTrue(accepts(normalized(6, 592)))
        assertTrue(accepts(normalized(6, 17)))
        assertTrue(accepts(plain(500)))
        assertTrue(accepts(normalized(6, 0, puzzle = "null")))
    }

    @Test
    fun `request never accepts a stream body or another game's by-id body`() {
        val accepts = zip.request(592).accepts
        // Today's stream / today's-game query: the same puzzle object, no games-by-id root.
        val stream = """1:{"trailGamePuzzle":{"gridSize":6},"todaysGameEditionText":"598"}"""
        val todaysGame = """{"data":{"identityDashGamesByTodaysGame":{"elements":[{"gamePuzzle":{"trailGamePuzzle":{"gridSize":6}},""" +
            """"entityUrn":"urn:li:fsd_game:($GUEST,6,598)"}]}}}"""

        assertFalse(accepts(stream))
        assertFalse(accepts(todaysGame))
        assertFalse(accepts(normalized(3, 592)))
        assertFalse(accepts(""))
    }

    @Test
    fun `check passes the requested edition with a puzzle, reading entityUrn and never puzzleId`() {
        assertNull(zip.check(normalized(6, 592), 592))
        assertNull(zip.check(plain(500), 500))
    }

    @Test
    fun `check refuses an entityUrn number other than the requested one as Untrusted`() {
        val error = zip.check(normalized(6, 591), 592)

        assertTrue(error is FetchError.Untrusted)
        // puzzleId (today's number) never stands in for the edition.
        assertTrue(zip.check(normalized(6, 591), 598) is FetchError.Untrusted)
    }

    @Test
    fun `check refuses a body without this game's entityUrn as Untrusted`() {
        assertTrue(zip.check(normalized(3, 592), 592) is FetchError.Untrusted)
        assertTrue(zip.check("{}", 592) is FetchError.Untrusted)
    }

    @Test
    fun `check reads a null puzzle as NotFound`() {
        assertTrue(zip.check(normalized(6, 100000, puzzle = "null"), 100000) is FetchError.NotFound)
        assertTrue(zip.check(normalized(6, 592, puzzle = " null"), 592) is FetchError.NotFound)
    }

    @Test
    fun `numberOrNull is null before day1 and the number from day1 on`() {
        assertNull(zip.numberOrNull(LocalDate.of(2025, 3, 17)))
        assertEquals(1, zip.numberOrNull(LocalDate.of(2025, 3, 18)))
        assertEquals(592, zip.numberOrNull(LocalDate.of(2026, 10, 30)))
    }

    @Test
    fun `fetch outside #1 to today's number is NotFound and sends no request`() = runTest {
        val transport = Recording(FetchResult.Ok(normalized(6, 599)))

        val tomorrow = zip.fetch(transport, day = TODAY.plusDays(1), today = TODAY)
        val beforeDay1 = zip.fetch(transport, day = zip.day1.minusDays(1), today = TODAY)

        assertTrue((tomorrow as FetchResult.Err).error is FetchError.NotFound)
        assertTrue((beforeDay1 as FetchResult.Err).error is FetchError.NotFound)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `fetch asks games-by-id for the day's number and passes a transport error through`() = runTest {
        val error = FetchError.Throttled("cooldown")
        val transport = Recording(FetchResult.Err(error))

        val result = zip.fetch(transport, day = PAST, today = TODAY)

        assertEquals(FetchResult.Err(error), result)
        val request = transport.requests.single()
        assertEquals(zip.request(592).persistedQuery?.variables, request.persistedQuery?.variables)
        assertFalse(request.stream)
    }

    @Test
    fun `fetch refuses a body of another edition as Untrusted`() = runTest {
        val result = zip.fetch(Recording(FetchResult.Ok(normalized(6, 591))), day = PAST, today = TODAY)

        assertTrue((result as FetchResult.Err).error is FetchError.Untrusted)
    }

    @Test
    fun `fetch reads a null puzzle as NotFound`() = runTest {
        val result = zip.fetch(Recording(FetchResult.Ok(normalized(6, 592, puzzle = "null"))), day = PAST, today = TODAY)

        assertTrue((result as FetchResult.Err).error is FetchError.NotFound)
    }

    @Test
    fun `fetch returns the checked body of the day's edition, today's included`() = runTest {
        val body = normalized(6, 592)

        assertEquals(FetchResult.Ok(body), zip.fetch(Recording(FetchResult.Ok(body)), day = PAST, today = TODAY))
        assertEquals(FetchResult.Ok(plain(598)), zip.fetch(Recording(FetchResult.Ok(plain(598))), day = TODAY, today = TODAY))
    }

    /** A transport that answers every request with [reply] and records what it was asked. */
    private class Recording(private val reply: FetchResult<String>) : FeedTransport {
        val requests = mutableListOf<FeedRequest>()
        override suspend fun fetch(request: FeedRequest): FetchResult<String> = reply.also { requests += request }
    }

    private companion object {
        const val GUEST = "ACoAAAAAAAABWzs7tPEzjbzdTu0bZDhDZpin_Ds"
        val TODAY: LocalDate = LocalDate.of(2026, 11, 5) // Zip #598
        val PAST: LocalDate = LocalDate.of(2026, 10, 30) // Zip #592
    }
}
