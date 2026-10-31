package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleSource
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import com.ivandsky.puzzlebox.games.zip.codec.ZipCodec
import com.ivandsky.puzzlebox.games.zip.codec.ZipWireParser
import com.ivandsky.puzzlebox.games.zip.engine.ZipRules
import com.ivandsky.puzzlebox.games.zip.engine.ZipSolver
import com.ivandsky.puzzlebox.data.network.LinkedInZipClient
import com.ivandsky.puzzlebox.data.network.TrailGameExtractor
import java.time.LocalDate

/**
 * The ADR-0002 LinkedInDaily adapter (ticket 12): wire body -> [ZipWireParser] -> the
 * A16 oracle gate -> canonical [ZipCodec] bytes in a [Puzzle] envelope. Implements
 * [PuzzleSource] for the archive seam; [fetchDaily] additionally carries the fetch-time
 * identity ([ZipPuzzleRef]) that core/data persists per ADR-0004.
 *
 * Error split per the FetchError contract: transport/parse failures are [FetchError.Unavailable];
 * a payload the engine cannot trust is [FetchError.Untrusted] — never playable (A16).
 */
class LinkedInZipDailySource(private val client: LinkedInZipClient) : PuzzleSource, ZipDailySource {

    override suspend fun fetch(date: LocalDate): FetchResult<Puzzle> =
        when (val result = fetchDaily(date)) {
            is FetchResult.Ok -> FetchResult.Ok(result.value.puzzle)
            is FetchResult.Err -> result
        }

    override suspend fun fetchDaily(date: LocalDate): FetchResult<ZipDailySource.Daily> {
        val body = when (val fetched = client.fetchDailyBody()) {
            is FetchResult.Ok -> fetched.value
            is FetchResult.Err -> return FetchResult.Err(fetched.error)
        }
        val match = TrailGameExtractor.extract(body)
            ?: return FetchResult.Err(FetchError.Unavailable("no trailGamePuzzle in body"))
        val daily = when (val parsed = ZipWireParser.parse(match.trailGamePuzzleJson, date, match.editionText)) {
            is DecodeResult.Ok -> parsed.value
            is DecodeResult.Err -> return FetchResult.Err(FetchError.Unavailable("wire parse failed: ${parsed.error}"))
        }
        oracleGate(daily)?.let { return FetchResult.Err(it) }
        val envelope = Puzzle(
            gameId = ZipDefinition.id,
            variant = daily.puzzle.variant,
            formatVersion = ZipCodec.VERSION,
            payload = ZipCodec.encode(daily.puzzle), // canonical bytes: storage + dedup (A19)
        )
        return FetchResult.Ok(ZipDailySource.Daily(envelope, daily.ref))
    }

    /**
     * A16 oracle (ZipDefinition recipe): the shipped solution must verify against the
     * rules AND agree with the exhaustive solver (exactly one solution, equal to shipped).
     */
    private fun oracleGate(daily: ZipWireParser.ZipDaily): FetchError? {
        val puzzle = daily.puzzle
        val shipped = puzzle.solution
            ?: return FetchError.Untrusted("daily ships no solution; A16 cannot validate")
        if (!ZipRules.verifyPath(puzzle, shipped)) {
            return FetchError.Untrusted("shipped solution violates zip rules")
        }
        val solver = ZipSolver()
        val count = solver.countSolutions(puzzle)
        if (count != 1) return FetchError.Untrusted("solver found $count solutions, need exactly 1")
        val found = solver.solve(puzzle)
        if (found != shipped) return FetchError.Untrusted("solver disagrees with shipped solution")
        return null
    }
}
