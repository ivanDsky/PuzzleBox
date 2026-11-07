package com.ivandsky.puzzlebox.games.strands

import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.game.map
import com.ivandsky.puzzlebox.games.strands.codec.StrandsCodec
import com.ivandsky.puzzlebox.games.strands.codec.StrandsSessionCodec
import com.ivandsky.puzzlebox.games.strands.editor.StrandsEditor
import com.ivandsky.puzzlebox.games.strands.engine.StrandsRules
import com.ivandsky.puzzlebox.games.strands.feed.NytStrandsDailySource
import com.ivandsky.puzzlebox.games.strands.render.StrandsRendererModel
import org.koin.core.module.Module
import org.koin.core.qualifier.qualifier
import org.koin.dsl.bind
import org.koin.dsl.module

/** The whole game in one injectable value (ADR-0001 §2, queensPlugin shape). */
val strandsPlugin: GamePlugin<StrandsState, StrandsSession, StrandsRendererModel> =
    GamePlugin(
        definition = StrandsDefinition,
        rules = StrandsRules,
        session = StrandsSessionCodec,
        score = StrandsScoreModel,
        stateOf = { session -> session.state },
        initialSession = { puzzle ->
            if (puzzle.gameId != StrandsDefinition.id) {
                DecodeResult.Err(DecodeError.Malformed("foreign envelope: ${puzzle.gameId}"))
            } else {
                StrandsCodec.decode(puzzle.payload).map { StrandsSession(ref = null, state = StrandsState(it)) }
            }
        },
        refOf = { key -> StrandsPuzzleRef(key) },
        replaySession = { cached, key ->
            StrandsCodec.decode(cached.payload).map { puzzle ->
                SessionBlob.encode(
                    StrandsDefinition.id,
                    StrandsSessionCodec.serialize(
                        StrandsSession(ref = StrandsPuzzleRef(key), state = StrandsState(puzzle)),
                    ),
                )
            }
        },
        rendererModel = { state, _, _ -> StrandsRendererModel.of(state) },
        editor = StrandsEditor,
    )

/**
 * Koin wiring (ADR-0001): `app` collects every plugin and every [DailySource]; the id
 * qualifier keeps another game's `single { ... }` from overwriting this plugin. The
 * NytTransport comes from core:network.
 */
val strandsModule: Module = module {
    single(qualifier = qualifier(StrandsDefinition.id.value)) { strandsPlugin }
    single { NytStrandsDailySource(get()) } bind DailySource::class
}
