package com.ivandsky.puzzlebox.games.queensui.trainer

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.codec.QueensWireParser
import com.ivandsky.puzzlebox.games.queens.feed.QueensGameExtractor
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers

/**
 * The real 7×7 daily no. 916 (copied from games:queens' test fixtures, whose test resources queens-ui cannot reach),
 * as a puzzle with its solution and as the app's canonical [Puzzle], and a trainer to hand it to.
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
}
