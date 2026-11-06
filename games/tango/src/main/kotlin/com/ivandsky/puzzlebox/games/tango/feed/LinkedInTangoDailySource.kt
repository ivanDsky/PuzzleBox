package com.ivandsky.puzzlebox.games.tango.feed

import com.ivandsky.puzzlebox.game.Cadence
import com.ivandsky.puzzlebox.game.DailyFetch
import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FeedRequest
import com.ivandsky.puzzlebox.game.FeedTransport
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.LinkedInEdition
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.codec.LotkaWireParser
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import com.ivandsky.puzzlebox.model.GameId
import java.time.Clock
import java.time.LocalDate

/**
 * The LinkedInDaily adapter for Tango (ADR-0002): wire body -> [LotkaWireParser] ->
 * A15 gate -> canonical [TangoCodec] bytes in a [Puzzle] envelope, with the fetch-time
 * identity ([com.ivandsky.puzzlebox.games.tango.TangoPuzzleRef]) core/data persists per
 * ADR-0004. Today comes through the generic [FeedTransport] ([REQUEST]: stream GET, then
 * the page-shell scan); a past day comes by number from games-by-id ([EDITION], ADR-0017).
 *
 * Error split per the FetchError contract: transport/parse failures are
 * [FetchError.Unavailable]; the throttle signature is [FetchError.Throttled] (the
 * transport decides from the wire, not the URL). A payload outside the shipped grid
 * sizes is [FetchError.Unavailable] (parse family) — ADR-0005 never guesses the variant. A
 * payload that parses but is not uniquely solvable, or a by-id body of another number, is
 * [FetchError.Untrusted] (fix-round-1 I1, Zip oracle parity; A6): trust is a
 * source-boundary responsibility, so the ambiguous daily is refused BEFORE the offline
 * cache can overwrite a good captured row — the play-time gate stays as defense-in-depth.
 * A number LinkedIn has no puzzle for, or one above today's, is [FetchError.NotFound].
 *
 * [clock] only bounds past numbers by today's (A4): LinkedIn serves tomorrow's puzzle too.
 */
class LinkedInTangoDailySource(
    private val transport: FeedTransport,
    private val clock: Clock = Clock.systemUTC(),
) : DailySource {

    override val gameId: GameId = TangoDefinition.id

    override val archiveSince: LocalDate = EDITION.day1

    override fun puzzleNumber(day: LocalDate): Int? = if (day.isBefore(archiveSince)) null else EDITION.number(day)

    override suspend fun fetchDaily(date: LocalDate): FetchResult<DailyFetch> {
        val body = when (val fetched = transport.fetch(REQUEST)) {
            is FetchResult.Ok -> fetched.value
            is FetchResult.Err -> return FetchResult.Err(fetched.error)
        }
        val match = TangoGameExtractor.extract(body)
            ?: return FetchResult.Err(FetchError.Unavailable("no ${TangoGameExtractor.KEY} in body"))
        return trusted(match.lotkaGamePuzzleJson, date, match.editionText)
    }

    override suspend fun fetchPast(date: LocalDate): FetchResult<DailyFetch> {
        val number = EDITION.number(date)
        val todays = EDITION.number(today())
        if (number !in 1..todays) {
            return FetchResult.Err(FetchError.NotFound("Tango #$number is outside #1..#$todays"))
        }
        val body = when (val fetched = transport.fetch(EDITION.request(number))) {
            is FetchResult.Ok -> fetched.value
            is FetchResult.Err -> return FetchResult.Err(fetched.error)
        }
        EDITION.check(body, number)?.let { return FetchResult.Err(it) }
        val match = TangoGameExtractor.extract(body)
            ?: return FetchResult.Err(FetchError.Unavailable("no ${TangoGameExtractor.KEY} in by-id body"))
        // By-id bodies carry no edition text; the number is the checked request's (A6).
        return trusted(match.lotkaGamePuzzleJson, date, number.toString())
    }

    /** Parse -> A15 uniqueness gate -> canonical envelope, shared by today's and past days' bodies. */
    private fun trusted(lotkaGamePuzzleJson: String, date: LocalDate, editionText: String?): FetchResult<DailyFetch> {
        val daily = when (val parsed = LotkaWireParser.parse(lotkaGamePuzzleJson, date, editionText)) {
            is DecodeResult.Ok -> parsed.value
            is DecodeResult.Err -> return FetchResult.Err(FetchError.Unavailable("wire parse failed: ${parsed.error}"))
        }
        // A15 at the source boundary (Zip oracle parity): a daily that parses but has
        // more than one solution is never trusted — and, downstream, never cached.
        if (!TangoUniqueness.isUniquelySolvable(daily.puzzle)) {
            return FetchResult.Err(FetchError.Untrusted("daily is not uniquely solvable"))
        }
        val envelope = Puzzle(
            gameId = TangoDefinition.id,
            variant = daily.puzzle.variant,
            formatVersion = TangoCodec.VERSION,
            payload = TangoCodec.encode(daily.puzzle), // canonical bytes: storage + dedup (A19)
        )
        return FetchResult.Ok(DailyFetch(envelope, daily.ref))
    }

    private fun today(): LocalDate = when (val cadence = TangoDefinition.cadence) {
        is Cadence.Daily -> LocalDate.now(clock.withZone(cadence.zone))
    }

    companion object {
        /** Tango's feed: the stream GET, then the page-shell scan (no GraphQL rung). */
        val REQUEST = FeedRequest(
            path = "games/tango/",
            objectName = TangoGameExtractor.KEY,
            accepts = { TangoGameExtractor.extract(it) != null },
            scanPageShell = true,
        )

        /** Tango's by-number edition: LinkedIn gameTypeId 5, #1 on 2024-10-08 PT (A3). */
        val EDITION = LinkedInEdition(
            gameTypeId = 5,
            day1 = LocalDate.of(2024, 10, 8),
            path = REQUEST.path,
            objectName = REQUEST.objectName,
        )
    }
}
