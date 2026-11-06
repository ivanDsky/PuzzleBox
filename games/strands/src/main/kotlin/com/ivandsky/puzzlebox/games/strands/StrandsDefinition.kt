package com.ivandsky.puzzlebox.games.strands

import com.ivandsky.puzzlebox.game.Cadence
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GameDefinition
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.strands.codec.StrandsCodec
import com.ivandsky.puzzlebox.model.GameId
import java.time.ZoneId

/**
 * Strands identity + storage codec (ADR-0001; QueensDefinition shape). Decode wraps the
 * original bytes so storage stays byte-exact (ADR-0004); encode produces the canonical form.
 * The day turns at America/Los_Angeles midnight like every other game (A11), so today's set,
 * the ring, streaks and "done" agree at any device zone.
 */
object StrandsDefinition : GameDefinition {

    /** The one rule regime: NYT's daily Strands. */
    val VARIANT: VariantId = VariantId("strands/nyt")

    override val id: GameId = GameId("strands")
    override val displayName: String = "Strands"
    override val cadence: Cadence = Cadence.Daily(ZoneId.of("America/Los_Angeles"))

    override fun decode(bytes: ByteArray): DecodeResult<Puzzle> =
        when (val typed = StrandsCodec.decode(bytes)) {
            is DecodeResult.Ok -> DecodeResult.Ok(
                Puzzle(gameId = id, variant = VARIANT, formatVersion = StrandsCodec.VERSION, payload = bytes),
            )
            is DecodeResult.Err -> typed
        }

    override fun encode(puzzle: Puzzle): ByteArray {
        require(puzzle.gameId == id) { "puzzle ${puzzle.gameId} is not a $id puzzle" }
        return when (val typed = StrandsCodec.decode(puzzle.payload)) {
            is DecodeResult.Ok -> StrandsCodec.encode(typed.value)
            is DecodeResult.Err ->
                throw IllegalArgumentException("payload of ${puzzle.gameId} is undecodable: ${typed.error}")
        }
    }
}
