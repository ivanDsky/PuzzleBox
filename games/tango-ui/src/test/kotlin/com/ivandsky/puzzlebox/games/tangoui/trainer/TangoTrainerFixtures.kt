package com.ivandsky.puzzlebox.games.tangoui.trainer

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.codec.LotkaWireParser
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import com.ivandsky.puzzlebox.games.tango.feed.TangoGameExtractor
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers

/**
 * Real 6×6 dailies (copied from games:tango's test fixtures, whose test resources tango-ui cannot reach). byid-1's
 * walk uses eight of the nine Tango techniques, every one but the split pair, which no bundled daily reaches.
 */
internal object TangoTrainerFixtures {

    val trainer = TangoTrainer(Dispatchers.Default)

    /** The daily byid-[number]'s puzzle. */
    fun puzzleOf(number: Int): TangoPuzzle {
        val body = TangoTrainerFixtures::class.java.getResourceAsStream("/fixtures/linkedin-tango-byid-$number.json")!!
            .readBytes().decodeToString()
        val match = checkNotNull(TangoGameExtractor.extract(body)) { "byid-$number holds a Tango game" }
        return when (val parsed = LotkaWireParser.parse(match.lotkaGamePuzzleJson, LocalDate.of(2026, 11, 5), match.editionText)) {
            is DecodeResult.Ok -> parsed.value.puzzle
            is DecodeResult.Err -> throw AssertionError("byid-$number parse failed: ${parsed.error}")
        }
    }

    /** byid-1's puzzle. */
    val puzzle: TangoPuzzle by lazy { puzzleOf(1) }

    /** [puzzle] as the app's canonical [Puzzle], the way a trainer is handed one. */
    val canonical: Puzzle by lazy { canonicalOf(puzzle) }

    /** Three dailies, as the canonical [Puzzle]s a trainer is handed. */
    val dailies: List<Puzzle> by lazy { listOf(1, 301, 758).map { canonicalOf(puzzleOf(it)) } }

    private fun canonicalOf(puzzle: TangoPuzzle): Puzzle = (TangoDefinition.decode(TangoCodec.encode(puzzle)) as DecodeResult.Ok).value
}
