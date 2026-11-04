package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.game.Cadence
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GameDefinition
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRules
import com.ivandsky.puzzlebox.game.Hint
import com.ivandsky.puzzlebox.game.Move
import com.ivandsky.puzzlebox.game.MoveVerdict
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.codec.QueensSessionCodec
import com.ivandsky.puzzlebox.games.queens.engine.hints.QueensHint
import com.ivandsky.puzzlebox.games.queens.engine.hints.QueensHints
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRuleId
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRules as EngineQueensRules
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensVerifier
import com.ivandsky.puzzlebox.games.queens.render.QueensRendererModel
import com.ivandsky.puzzlebox.model.GameId
import java.time.ZoneId
import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.games.queens.feed.LinkedInQueensDailySource
import org.koin.core.module.Module
import org.koin.core.qualifier.qualifier
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Queens identity + storage codec (ADR-0001; zip ZipDefinition shape). Decode wraps the
 * ORIGINAL bytes — storage stays byte-exact (ADR-0004); encode produces the canonical
 * form. The A13 uniqueness gate (shipped solution must match an independent solve) is a
 * decode-time check that lands with the solver ticket (queens-02) — the wire parser
 * already carries [QueensPuzzle.solution] so the gate needs no envelope change.
 */
object QueensDefinition : GameDefinition {

    override val id = GameId("queens")
    override val displayName = "Queens"
    override val cadence: Cadence = Cadence.Daily(ZoneId.of("America/Los_Angeles")) // zip daily zone

    override fun decode(bytes: ByteArray): DecodeResult<Puzzle> =
        when (val typed = QueensCodec.decode(bytes)) {
            is DecodeResult.Ok -> DecodeResult.Ok(
                Puzzle(
                    gameId = id,
                    variant = queensVariantId(typed.value.size),
                    formatVersion = QueensCodec.VERSION,
                    payload = bytes, // original bytes own storage; never re-encoded on read
                ),
            )

            is DecodeResult.Err -> DecodeResult.Err(typed.error)
        }

    override fun encode(puzzle: Puzzle): ByteArray {
        require(puzzle.gameId == id) { "puzzle ${puzzle.gameId} is not a $id puzzle" }
        return when (val typed = QueensCodec.decode(puzzle.payload)) {
            is DecodeResult.Ok -> QueensCodec.encode(typed.value) // canonical form for storage (A19)
            is DecodeResult.Err ->
                throw IllegalArgumentException("payload of ${puzzle.gameId} is undecodable: ${typed.error}")
        }
    }
}

/**
 * The rules state: the decoded puzzle plus the placed queens — the A10–A12 session is
 * the source of truth; [com.ivandsky.puzzlebox.games.queens.render.QueensRendererModel]
 * and generic plugin consumers (ADR-0001) read this projection of it.
 */
data class QueensState(
    val puzzle: QueensPuzzle,
    val queens: Set<Cell> = emptySet(),
)

/** Game-authored moves for generic plugin consumers (zip ZipMove precedent). The play
 *  UI drives the session directly; this seam is the plugin contract surface. */
sealed interface QueensPlayMove : Move {
    /** Place a queen on an empty cell (A10; auto-fill is a session side effect). */
    data class Place(val cell: Cell) : QueensPlayMove

    /** Remove the queen at [cell] (A10 lifecycle). */
    data class Remove(val cell: Cell) : QueensPlayMove

    /** Clear every placed queen. */
    data object Reset : QueensPlayMove
}

/**
 * Typed validation outcome. [QueensValidation.Invalid.rule] is the first violated rule
 * id (pipeline order) for a rule-breaking placement; null covers the structural cases
 * (out of bounds, cell occupied / empty on remove) and foreign moves.
 */
sealed interface QueensValidation : MoveVerdict {
    data object Valid : QueensValidation
    data class Invalid(val rule: QueensRuleId? = null) : QueensValidation
}

/**
 * Real rules (ADR-0001 seam; the queens-01 skeleton bodies are gone): a thin, stateless
 * facade over the engine — [EngineQueensRules] pipeline for placements,
 * [QueensVerifier] for completeness, [QueensHints] for the typed hint payload.
 * Validation is total: never throws, never unchecked-casts.
 */
object QueensGameRules : GameRules<QueensState> {

    private val hints = QueensHints()

    override fun validate(move: Move, state: QueensState): MoveVerdict = when (move) {
        is QueensPlayMove.Place ->
            if (move.cell.idx !in 0 until state.puzzle.cellCount || move.cell in state.queens) {
                QueensValidation.Invalid()
            } else {
                EngineQueensRules.placementViolatedBy(move.cell, state.queens, state.puzzle)
                    ?.let(QueensValidation::Invalid)
                    ?: QueensValidation.Valid
            }
        is QueensPlayMove.Remove ->
            if (move.cell in state.queens) QueensValidation.Valid else QueensValidation.Invalid()
        QueensPlayMove.Reset -> QueensValidation.Valid
        // Foreign move (another game's Move reaching this validate): typed rejection.
        else -> QueensValidation.Invalid()
    }

    override fun isComplete(state: QueensState): Boolean =
        QueensVerifier.isSolution(state.puzzle, state.queens)

    override fun hint(state: QueensState): Hint = hints.hint(state.puzzle, state.queens.toList())
}

/** The whole game in one injectable value (ADR-0001 §2, zip zipPlugin shape). */
val queensPlugin: GamePlugin<QueensState, QueensSession, QueensRendererModel> =
    GamePlugin(
        definition = QueensDefinition,
        rules = QueensGameRules,
        session = QueensSessionCodec,
        score = QueensScoreModel(),
        // The full session state projects in: queens (and thus the real renderer model).
        stateOf = { session -> QueensState(session.puzzle, session.queens.toSet()) },
        initialSession = { puzzle ->
            if (puzzle.gameId != QueensDefinition.id) {
                DecodeResult.Err(DecodeError.Malformed("foreign envelope: ${puzzle.gameId}"))
            } else {
                when (val decoded = QueensCodec.decode(puzzle.payload)) {
                    is DecodeResult.Ok -> DecodeResult.Ok(QueensSession(ref = null, puzzle = decoded.value))
                    is DecodeResult.Err -> DecodeResult.Err(decoded.error)
                }
            }
        },
        refOf = { date, puzzleNumber -> QueensPuzzleRef(date, puzzleNumber) },
        // Replay hand-off: zip's shape — the play state trusts any payload the codec decodes.
        replaySession = { cached, day, puzzleNumber ->
            when (val decoded = QueensCodec.decode(cached.payload)) {
                is DecodeResult.Ok -> DecodeResult.Ok(
                    SessionBlob.encode(
                        QueensDefinition.id,
                        QueensSessionCodec.serialize(
                            QueensSession(
                                ref = QueensPuzzleRef(date = day, puzzleNumber = puzzleNumber),
                                puzzle = decoded.value,
                            ),
                        ),
                    ),
                )

                is DecodeResult.Err -> DecodeResult.Err(decoded.error)
            }
        },
        rendererModel = { state, hint, _ ->
            QueensRendererModel.of(state.puzzle, state.queens.map { it.idx }.toSet(), hint)
        },
    )

/**
 * Koin wiring (ADR-0001): `app` collects every `GamePlugin<*, *, *>` into the GameRegistry
 * and every [DailySource] into the daily repository; the id qualifier stops another game's
 * `single { ... }` from overwriting Queens' definition while `getAll` still collects it.
 */
val queensModule: Module = module {
    single(qualifier = qualifier(QueensDefinition.id.value)) { queensPlugin }
    single { LinkedInQueensDailySource(get()) } bind DailySource::class
}
