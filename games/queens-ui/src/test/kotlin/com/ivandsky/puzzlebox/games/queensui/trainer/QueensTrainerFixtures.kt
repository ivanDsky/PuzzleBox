package com.ivandsky.puzzlebox.games.queensui.trainer

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.codec.QueensWireParser
import com.ivandsky.puzzlebox.games.queens.engine.trainer.QueensMoveCheck
import com.ivandsky.puzzlebox.games.queens.engine.trainer.QueensTrainerMove
import com.ivandsky.puzzlebox.games.queens.feed.QueensGameExtractor
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers

/**
 * The real 7×7 daily no. 916 (copied from games:queens' test fixtures, whose test resources queens-ui cannot reach),
 * its snippets for every Queens technique, and what the move check says of each open square.
 */
internal object QueensTrainerFixtures {

    val trainer = QueensTrainer(Dispatchers.Default)

    /** The daily's puzzle with its solution. */
    val puzzle: QueensPuzzle by lazy {
        val body = QueensTrainerFixtures::class.java.getResourceAsStream("/fixtures/queens-daily-real-916.json")!!
            .readBytes().decodeToString()
        val wire = QueensGameExtractor.extract(body)?.queensGamePuzzleJson ?: body
        when (val parsed = QueensWireParser.parse(wire, LocalDate.of(2026, 11, 2))) {
            is DecodeResult.Ok -> parsed.value.puzzle
            is DecodeResult.Err -> throw AssertionError("daily 916 parse failed: ${parsed.error}")
        }
    }

    /** [puzzle] as the app's canonical [Puzzle], the way a trainer is handed one. */
    val canonical: Puzzle by lazy { (QueensDefinition.decode(QueensCodec.encode(puzzle)) as DecodeResult.Ok).value }

    /** Up to four snippets of every technique in the catalog, in catalog order. */
    val snippets: List<QueensSnippet> by lazy {
        QueensTrainerCatalog.techniques.flatMap { trainer.buildSnippets(canonical, it.id, 4) }.map { it as QueensSnippet }
    }

    /** The move the board takes from a tap on [cell] of [snippet]. */
    fun moveAt(snippet: QueensSnippet, cell: Int): QueensTrainerMove =
        if (snippet.placesQueen) QueensTrainerMove.Place(Cell(cell)) else QueensTrainerMove.Cross(Cell(cell))

    /** The squares of [snippet] the board takes a tap on, each with the move check's verdict on that tap. */
    fun openSquares(snippet: QueensSnippet): Map<Int, TrainerVerdict> {
        val session = QueensSnippetBoardState(snippet).session
        return (0 until snippet.puzzle.cellCount)
            .filter { Cell(it) !in session.queens && session.markAt(Cell(it)) == null }
            .associateWith { cell ->
                QueensMoveCheck.classify(snippet.puzzle, snippet.cut.boardBefore, snippet.technique, moveAt(snippet, cell))
            }
    }

    /** The first snippet with an open square the move check judges [verdict], and that square; null when none has one. */
    fun snippetWith(verdict: TrainerVerdict): Pair<QueensSnippet, Int>? = snippets.firstNotNullOfOrNull { snippet ->
        openSquares(snippet).entries.firstOrNull { it.value == verdict }?.let { snippet to it.key }
    }
}
