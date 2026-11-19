package com.ivandsky.puzzlebox.games.tangoui.trainer

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.codec.LotkaWireParser
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import com.ivandsky.puzzlebox.games.tango.engine.trainer.TangoMoveCheck
import com.ivandsky.puzzlebox.games.tango.engine.trainer.TangoTrainerMove
import com.ivandsky.puzzlebox.games.tango.feed.TangoGameExtractor
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers

/**
 * The real 6×6 daily byid-1 (copied from games:tango's test fixtures, whose test resources tango-ui cannot reach): its
 * walk uses eight of the nine Tango techniques (every one but the split pair, which no bundled daily reaches), its
 * snippets for each, and what the move check says of each open cell.
 */
internal object TangoTrainerFixtures {

    val trainer = TangoTrainer(Dispatchers.Default)

    /** The daily's puzzle. */
    val puzzle: TangoPuzzle by lazy {
        val body = TangoTrainerFixtures::class.java.getResourceAsStream("/fixtures/linkedin-tango-byid-1.json")!!
            .readBytes().decodeToString()
        val match = checkNotNull(TangoGameExtractor.extract(body)) { "byid-1 holds a Tango game" }
        when (val parsed = LotkaWireParser.parse(match.lotkaGamePuzzleJson, LocalDate.of(2026, 11, 5), match.editionText)) {
            is DecodeResult.Ok -> parsed.value.puzzle
            is DecodeResult.Err -> throw AssertionError("byid-1 parse failed: ${parsed.error}")
        }
    }

    /** [puzzle] as the app's canonical [Puzzle], the way a trainer is handed one. */
    val canonical: Puzzle by lazy { (TangoDefinition.decode(TangoCodec.encode(puzzle)) as DecodeResult.Ok).value }

    /** Up to four snippets of every technique in the catalog, in catalog order. */
    val snippets: List<TangoSnippet> by lazy {
        TangoTrainerCatalog.techniques.flatMap { trainer.buildSnippets(canonical, it.id, 4) }.map { it as TangoSnippet }
    }

    /** The move the board takes from a tap on [idx] of [snippet]: the kind the target step places. */
    fun moveAt(snippet: TangoSnippet, idx: Int): TangoTrainerMove = TangoTrainerMove(idx, snippet.value)

    /** The cells of [snippet] the board takes a tap on (empty in the cut), each with the move check's verdict on that tap. */
    fun openCells(snippet: TangoSnippet): Map<Int, TrainerVerdict> =
        (0 until snippet.puzzle.cellCount)
            .filter { snippet.cut.gridBefore[it] == null }
            .associateWith { idx ->
                TangoMoveCheck.classify(snippet.puzzle, snippet.solution, snippet.cut.gridBefore, snippet.technique, moveAt(snippet, idx))
            }

    /** The first snippet with an open cell the move check judges [verdict], and that cell; null when none has one. */
    fun snippetWith(verdict: TrainerVerdict): Pair<TangoSnippet, Int>? = snippets.firstNotNullOfOrNull { snippet ->
        openCells(snippet).entries.firstOrNull { it.value == verdict }?.let { snippet to it.key }
    }
}
