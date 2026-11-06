package com.ivandsky.puzzlebox.games.tango.feed

import com.ivandsky.puzzlebox.game.DailyFetch
import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FeedRequest
import com.ivandsky.puzzlebox.game.FeedTransport
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.codec.LotkaWireParser
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/**
 * The LinkedInDaily adapter for Tango (ADR-0002): wire body -> [LotkaWireParser] ->
 * A15 gate -> canonical [TangoCodec] bytes in a [Puzzle] envelope, with the fetch-time
 * identity ([com.ivandsky.puzzlebox.games.tango.TangoPuzzleRef]) core/data persists per
 * ADR-0004. The body comes through the generic [FeedTransport] ([REQUEST]: stream GET,
 * then the page-shell scan).
 *
 * Error split per the FetchError contract: transport/parse failures are
 * [FetchError.Unavailable]; the throttle signature is [FetchError.Throttled] (the
 * transport decides from the wire, not the URL). A payload outside the shipped grid
 * sizes is [FetchError.Unavailable] (parse family) — ADR-0005 never guesses the variant. A
 * payload that parses but is not uniquely solvable is [FetchError.Untrusted]
 * (fix-round-1 I1, Zip oracle parity): trust is a source-boundary responsibility, so
 * the ambiguous daily is refused BEFORE the offline cache can overwrite a good
 * captured row — the play-time gate stays as defense-in-depth.
 */
class LinkedInTangoDailySource(private val transport: FeedTransport) : DailySource {

    override val gameId: GameId = TangoDefinition.id

    override suspend fun fetchDaily(date: LocalDate): FetchResult<DailyFetch> {
        val body = when (val fetched = transport.fetch(REQUEST)) {
            is FetchResult.Ok -> fetched.value
            is FetchResult.Err -> return FetchResult.Err(fetched.error)
        }
        val match = TangoGameExtractor.extract(body)
            ?: return FetchResult.Err(FetchError.Unavailable("no ${TangoGameExtractor.KEY} in body"))
        val daily = when (val parsed = LotkaWireParser.parse(match.lotkaGamePuzzleJson, date, match.editionText)) {
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

    companion object {
        /** Tango's feed: the stream GET, then the page-shell scan (no GraphQL rung). */
        val REQUEST = FeedRequest(
            path = "games/tango/",
            objectName = TangoGameExtractor.KEY,
            accepts = { TangoGameExtractor.extract(it) != null },
            scanPageShell = true,
        )
    }
}
