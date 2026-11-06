package com.ivandsky.puzzlebox.games.sudoku

import com.ivandsky.puzzlebox.game.Cadence
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GameDefinition
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuCodec
import com.ivandsky.puzzlebox.model.GameId
import java.time.ZoneId

/**
 * One Sudoku game. The engine is shared, so a game is just an identity bound to the single
 * [variant] it plays; a payload of another variant is refused rather than played under the
 * wrong game.
 */
class SudokuDefinition(
    override val id: GameId,
    override val displayName: String,
    val variant: SudokuVariant,
) : GameDefinition {

    override val cadence: Cadence = Cadence.Daily(ZoneId.of("America/Los_Angeles"))

    /** The payload as this game's puzzle; another variant's board is [DecodeError.VariantSizeMismatch]. */
    fun decodePuzzle(bytes: ByteArray): DecodeResult<SudokuPuzzle> = when (val decoded = SudokuCodec.decode(bytes)) {
        is DecodeResult.Err -> decoded
        is DecodeResult.Ok ->
            if (decoded.value.variant == variant) {
                decoded
            } else {
                DecodeResult.Err(
                    DecodeError.VariantSizeMismatch(variant.id.value, variant.geometry.size, decoded.value.geometry.size),
                )
            }
    }

    /** Wraps the original bytes; the codec has exactly one encoding per puzzle, so they are canonical. */
    override fun decode(bytes: ByteArray): DecodeResult<Puzzle> = when (val decoded = decodePuzzle(bytes)) {
        is DecodeResult.Err -> decoded
        is DecodeResult.Ok -> DecodeResult.Ok(Puzzle(id, variant.id, SudokuCodec.VERSION, bytes))
    }

    override fun encode(puzzle: Puzzle): ByteArray {
        require(puzzle.gameId == id) { "foreign envelope ${puzzle.gameId} for $id" }
        return when (val decoded = decodePuzzle(puzzle.payload)) {
            is DecodeResult.Ok -> SudokuCodec.encode(decoded.value)
            is DecodeResult.Err -> throw IllegalArgumentException("undecodable $id payload: ${decoded.error}")
        }
    }

    override fun toString(): String = "SudokuDefinition($id)"
}
