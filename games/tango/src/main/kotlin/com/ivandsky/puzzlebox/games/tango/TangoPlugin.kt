package com.ivandsky.puzzlebox.games.tango

import com.ivandsky.puzzlebox.game.Cadence
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GameDefinition
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import com.ivandsky.puzzlebox.games.tango.codec.TangoSessionCodec
import com.ivandsky.puzzlebox.games.tango.engine.TangoRules
import com.ivandsky.puzzlebox.games.tango.engine.TangoState
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel
import com.ivandsky.puzzlebox.model.GameId
import java.time.ZoneId
import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.games.tango.feed.LinkedInTangoDailySource
import com.ivandsky.puzzlebox.games.tango.feed.TangoSeedBackedSource
import org.koin.core.module.Module
import org.koin.core.qualifier.qualifier
import org.koin.dsl.bind
import org.koin.dsl.module

/** ZipDefinition shape (A16 recipe): decode the fetch payload into a typed [Puzzle] envelope. */
object TangoDefinition : GameDefinition {

    override val id = GameId("tango")
    override val displayName = "Tango"
    override val cadence: Cadence = Cadence.Daily(ZoneId.of("America/Los_Angeles")) // A10

    override fun decode(bytes: ByteArray): DecodeResult<Puzzle> = when (val typed = TangoCodec.decode(bytes)) {
        is DecodeResult.Ok -> DecodeResult.Ok(
            Puzzle(
                gameId = id,
                variant = typed.value.variant,
                formatVersion = TangoCodec.VERSION,
                payload = bytes, // original bytes own storage; never re-encoded on read
            ),
        )

        is DecodeResult.Err -> DecodeResult.Err(typed.error)
    }

    override fun encode(puzzle: Puzzle): ByteArray {
        require(puzzle.gameId == id) { "puzzle ${puzzle.gameId} is not a $id puzzle" }
        return when (val typed = TangoCodec.decode(puzzle.payload)) {
            is DecodeResult.Ok -> TangoCodec.encode(typed.value) // canonical form for storage (A19)
            is DecodeResult.Err ->
                throw IllegalArgumentException("payload of ${puzzle.gameId} is undecodable: ${typed.error}")
        }
    }
}

/** The whole game in one injectable value (A16 §2). */
val tangoPlugin: GamePlugin<TangoState, TangoSession, TangoRendererModel> =
    GamePlugin(
        definition = TangoDefinition,
        rules = TangoRules,
        session = TangoSessionCodec,
        score = TangoScoreModel(),
        stateOf = { session -> TangoState(session.puzzle, session.grid()) },
        initialSession = { puzzle ->
            when (val decoded = TangoCodec.decode(puzzle.payload)) {
                is DecodeResult.Ok -> DecodeResult.Ok(TangoSession(ref = null, puzzle = decoded.value))
                is DecodeResult.Err -> DecodeResult.Err(decoded.error)
            }
        },
        refOf = { date, puzzleNumber -> TangoPuzzleRef(date, puzzleNumber) },
        rendererModel = { state, hint, verdict -> TangoRendererModel.of(state, hint, verdict) },
    )

/**
 * Koin wiring (ADR-0001): the id qualifier stops cross-game `single` overwrites. The
 * daily source is the seed-backed LinkedIn adapter (Tango-only fallback) over the shared
 * [com.ivandsky.puzzlebox.game.FeedTransport].
 */
val tangoModule: Module = module {
    single(qualifier = qualifier(TangoDefinition.id.value)) { tangoPlugin }
    single { TangoSeedBackedSource(LinkedInTangoDailySource(get())) } bind DailySource::class
}
