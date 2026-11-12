package com.ivandsky.puzzlebox.game

import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.replay.GameReplay
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.SolveReplay

/** Marker for a game's pure drawable model. :core:game-ui maps it to Compose by [com.ivandsky.puzzlebox.model.GameId];
 *  no Compose ever appears in core or in the game module (finding F2-1). */
interface RendererModel

/**
 * Composition root per game (ADR-0001) — the one type `app` aggregates into the
 * [GameRegistry] (each game module registers its plugin in Koin).
 *
 * Pure by design (finding F2-1, user-approved amendment): the renderer-model factory is a
 * plain function `state, hint, lastVerdict → drawable model`; the Compose mapping lives in
 * :core:game-ui, keyed by [definition]/[GameDefinition.id].
 *
 * Play-loop surface: generic consumers drive fetch → session → state → render without
 * knowing the game — [initialSession] builds a fresh session from a fetched [Puzzle]
 * (typed rejection for a foreign envelope), [stateOf] derives the rules state from a
 * restored (or freshly built) session, and [rendererModel] projects state + hint + the
 * last move's verdict onto the drawable model. [refOf] rebuilds the game's typed
 * [PuzzleRef] from its persisted [PuzzleKey] (a day's daily or a friend's custom puzzle,
 * ADR-0018), so storage round-trips refs without knowing any game. [playAgainSession] turns a
 * cached [Puzzle] into the play route's relaunch hand-off: the [SessionBlob]-encoded bytes of
 * a fresh session carrying that key, or the typed reason it can't be reopened (A16) —
 * History reopens any game, and a friend's puzzle reopens, without naming one.
 *
 * [editor] is the game's pure puzzle editor (ADR-0019, spec A1/A28): a game has an editor
 * iff its plugin carries one and its UI module binds the matching `GameEditorUi`.
 *
 * [replay] is the game's optional solve-replay adapter (ADR-0008); null hides every replay surface.
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
    val refOf: (key: PuzzleKey) -> PuzzleRef,
    val playAgainSession: (cached: Puzzle, key: PuzzleKey) -> DecodeResult<ByteArray>,
    val rendererModel: (state: P, hint: Hint?, lastVerdict: MoveVerdict?) -> M,
    val editor: GameEditor<*>? = null,
    val replay: GameReplay<S>? = null,
) {
    /**
     * Type-erased replay entry: [S] is in scope here, so no caller casts. Null = no [replay] member, a puzzle
     * this game rejects ([initialSession] is not Ok), or bytes [Timeline.open] refuses. Never throws.
     */
    fun openTimeline(puzzle: Puzzle, bytes: SolveReplay): Timeline? {
        val replay = replay ?: return null
        val initial = initialSession(puzzle).getOrNull() ?: return null
        return Timeline.open(definition.id, replay, initial, bytes)
    }
}
