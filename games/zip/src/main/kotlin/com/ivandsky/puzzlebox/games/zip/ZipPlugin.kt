package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.game.Cadence
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GameDefinition
import com.ivandsky.puzzlebox.game.GameId
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.zip.codec.ZipCodec
import com.ivandsky.puzzlebox.games.zip.codec.ZipSessionCodec
import com.ivandsky.puzzlebox.games.zip.engine.ZipRules
import com.ivandsky.puzzlebox.games.zip.engine.ZipVariants
import com.ivandsky.puzzlebox.games.zip.render.ZipRendererModel
import java.time.ZoneId
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The A16 recipe contract surface: decode the fetch payload, verify the shipped solution
 * against the rules, agree with the solver (count == 1 and found == shipped), THEN trust.
 * Verdicts: VerifySolutionOk -> play; VerifySolutionInvalid -> Untrusted; solver disagrees
 * (0 or >1 solutions) -> Untrusted. Production wiring: ticket 12.
 */
object ZipDefinition : GameDefinition {

    override val id = GameId("zip")
    override val displayName = "Zip"
    override val cadence: Cadence = Cadence.Daily(ZoneId.of("America/Los_Angeles")) // A19 §8

    override fun decode(bytes: ByteArray): DecodeResult<Puzzle> = when (val typed = ZipCodec.decode(bytes)) {
        is DecodeResult.Ok -> DecodeResult.Ok(
            Puzzle(
                gameId = id,
                variant = typed.value.variant,
                formatVersion = ZipCodec.VERSION,
                payload = bytes, // ADR-0004/§4: original bytes own storage; never re-encoded on read
            ),
        )

        is DecodeResult.Err -> DecodeResult.Err(typed.error)
    }

    override fun encode(puzzle: Puzzle): ByteArray {
        require(puzzle.gameId == id) { "puzzle ${puzzle.gameId} is not a $id puzzle" }
        return when (val typed = ZipCodec.decode(puzzle.payload)) {
            is DecodeResult.Ok -> ZipCodec.encode(typed.value) // canonical form for storage (A19)
            is DecodeResult.Err ->
                throw IllegalArgumentException("payload of ${puzzle.gameId} is undecodable: ${typed.error}")
        }
    }
}

/** The whole game in one injectable value (A16 §2): definition + rules + codecs + score + renderer model. */
val zipPlugin: GamePlugin<com.ivandsky.puzzlebox.games.zip.engine.ZipState, ZipSession, ZipRendererModel> =
    GamePlugin(
        definition = ZipDefinition,
        rules = ZipRules(ZipVariants.table),
        session = ZipSessionCodec,
        score = ZipScoreModel(),
        rendererModel = { state, hint -> ZipRendererModel.of(state, hint) },
    )

/** Koin wiring: feature modules resolve `GamePlugin<*, *, *>` instances generically (F2 §1). */
val zipModule: Module = module {
    single { zipPlugin }
}
