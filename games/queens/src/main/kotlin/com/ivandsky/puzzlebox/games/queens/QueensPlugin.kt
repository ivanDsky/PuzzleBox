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
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.codec.QueensSessionCodec
import com.ivandsky.puzzlebox.games.queens.render.QueensRendererModel
import com.ivandsky.puzzlebox.model.GameId
import java.time.ZoneId
import org.koin.core.module.Module
import org.koin.core.qualifier.qualifier
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
 * The rules state — SKELETON (queens-01): the decoded puzzle plus the placed queens.
 * Marks, provenance, and move validation state land with the rules tickets (queens-02+).
 */
data class QueensState(
    val puzzle: QueensPuzzle,
    val queens: Set<Cell> = emptySet(),
)

/** Move marker for the play loop (zip ZipMove precedent). The real moves are queens-02+. */
data object QueensMove : Move

/**
 * Typed rejection for every move until the real rules verdict ([QueensRulesVerdict],
 * queens-02's shape) exists. Validation is total: never throws, never unchecked-casts.
 */
data object QueensMoveRejected : MoveVerdict

/** Hint marker (game-typed presentation payload, F3). Real hints are queens-02+. */
data object QueensHint : Hint

/**
 * Rules — SKELETON (queens-01) pinning the [GameRules] seam only. The row/column/region
 * exclusivity + no-adjacency rule set (A1, A9, A10–A12 provenance) is queens-02's
 * deliverable and replaces the bodies below; signatures stay.
 */
object QueensRules : GameRules<QueensState> {

    override fun validate(move: Move, state: QueensState): MoveVerdict = QueensMoveRejected

    override fun isComplete(state: QueensState): Boolean = false

    override fun hint(state: QueensState): Hint = QueensHint
}

/** The whole game in one injectable value (ADR-0001 §2, zip zipPlugin shape). */
val queensPlugin: GamePlugin<QueensState, QueensSession, QueensRendererModel> =
    GamePlugin(
        definition = QueensDefinition,
        rules = QueensRules,
        session = QueensSessionCodec,
        score = QueensScoreModel(),
        stateOf = { session -> QueensState(session.puzzle) },
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
        rendererModel = { state, hint, _ ->
            QueensRendererModel.of(
                state.puzzle,
                state.queens.map { it.idx }.toSet(),
                hint,
            )
        },
    )

/**
 * Koin wiring (ADR-0001): feature modules resolve `GamePlugin<*, *, *>` generically; the
 * id qualifier stops another game's `single { ... }` from overwriting Queens' definition
 * while `getAll` still collects it.
 */
val queensModule: Module = module {
    single(qualifier = qualifier(QueensDefinition.id.value)) { queensPlugin }
}
