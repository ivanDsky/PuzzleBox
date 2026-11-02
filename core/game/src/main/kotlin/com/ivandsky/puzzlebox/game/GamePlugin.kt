package com.ivandsky.puzzlebox.game

/** Marker for a game's pure drawable model. :core:game-ui maps it to Compose by [com.ivandsky.puzzlebox.model.GameId];
 *  no Compose ever appears in core or in the game module (finding F2-1). */
interface RendererModel

/**
 * Composition root per game (ADR-0001) — the one type `app` aggregates
 * (`List<GamePlugin<*, *, *>>`, registered in Koin by each game module).
 *
 * Pure by design (finding F2-1, user-approved amendment): the renderer-model factory is a
 * plain function `state, hint, lastVerdict → drawable model`; the Compose mapping lives in
 * :core:game-ui, keyed by [definition]/[GameDefinition.id].
 *
 * Play-loop surface: generic consumers drive fetch → session → state → render without
 * knowing the game — [initialSession] builds a fresh session from a fetched [Puzzle]
 * (typed rejection for a foreign envelope), [stateOf] derives the rules state from a
 * restored (or freshly built) session, and [rendererModel] projects state + hint + the
 * last move's verdict onto the drawable model.
 *
 * @param P the game's rules state (input to [rules] and [rendererModel])
 * @param S the game's persisted session type
 * @param M the game's pure renderer model
 */
class GamePlugin<P, S : GameSession, M : RendererModel>(
    val definition: GameDefinition,
    val rules: GameRules<P>,
    val session: GameSessionCodec<S>,
    val score: ScoreModel<S>,
    val stateOf: (session: S) -> P,
    val initialSession: (puzzle: Puzzle) -> DecodeResult<S>,
    val rendererModel: (state: P, hint: Hint?, lastVerdict: MoveVerdict?) -> M,
)
