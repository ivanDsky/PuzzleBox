package com.ivandsky.puzzlebox.games.strands.feed

import com.ivandsky.puzzlebox.game.DailyFetch
import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.NytTransport
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.StrandsNumber
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzle
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzleRef
import com.ivandsky.puzzlebox.games.strands.StrandsWord
import com.ivandsky.puzzlebox.games.strands.codec.StrandsCodec
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate

/**
 * Strands' daily feed (ADR-0002, ADR-0012): any day from #1 on, one GET per day of
 * `svc/strands/v2/<date>.json` → [StrandsWireParser] → [StrandsTrustGate] → canonical
 * [StrandsCodec] envelope with a date-derived [StrandsPuzzleRef] (A10).
 *
 * Transport errors pass through (a 404 stays [FetchError.NotFound]); a body that is not the
 * wire shape is [FetchError.Unavailable], as for the LinkedIn sources; a day the gate rejects
 * is [FetchError.Untrusted] — never playable.
 */
class NytStrandsDailySource(private val transport: NytTransport) : DailySource {

    override val gameId: GameId = StrandsDefinition.id

    override val archiveSince: LocalDate = StrandsNumber.EPOCH

    override fun puzzleNumber(day: LocalDate): Int? = if (day.isBefore(archiveSince)) null else StrandsNumber.of(day)

    override suspend fun fetchDaily(date: LocalDate): FetchResult<DailyFetch> {
        val body = when (val fetched = transport.get("svc/strands/v2/$date.json")) {
            is FetchResult.Ok -> fetched.value
            is FetchResult.Err -> return fetched
        }
        val wire = when (val parsed = StrandsWireParser.parse(body)) {
            is DecodeResult.Ok -> parsed.value
            is DecodeResult.Err -> return FetchResult.Err(FetchError.Unavailable("wire parse failed: ${parsed.error}"))
        }
        StrandsTrustGate.check(wire, date)?.let { return FetchResult.Err(FetchError.Untrusted(it)) }
        val puzzle = wire.toPuzzle()
        return FetchResult.Ok(
            DailyFetch(StrandsCodec.envelope(puzzle), StrandsPuzzleRef(PuzzleKey.Daily(date, StrandsNumber.of(date)))),
        )
    }

    /** Only called on a wire the gate trusted, so every field is present and every coord a pair. */
    private fun StrandsWire.toPuzzle(): StrandsPuzzle {
        val board = startingBoard!!
        val themes = themeWords!!.map { word -> word(word, themeCoords!!.getValue(word)) }
        val spangramWord = word(spangram!!, spangramCoords!!)
        return StrandsPuzzle(
            rows = board.size,
            cols = board.first().length,
            letters = board.joinToString("").uppercase(),
            clue = clue!!,
            spangram = spangramWord,
            themeWords = themes,
            validWords = (solutions!! + themeWords + spangram).mapTo(LinkedHashSet()) { it.uppercase() },
            constructors = constructors!!,
        )
    }

    private fun word(text: String, coords: List<List<Int>>): StrandsWord =
        StrandsWord(text.uppercase(), coords.map { (row, col) -> Coord(row, col) })
}
