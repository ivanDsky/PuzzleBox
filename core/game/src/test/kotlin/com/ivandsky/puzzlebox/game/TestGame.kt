package com.ivandsky.puzzlebox.game

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate
import java.time.ZoneId

/**
 * A complete fake game exercising every contract member — the proof that the contract set
 * is sufficient to onboard a game with ZERO core edits (F1 §5). Zip is the real consumer;
 * this fake keeps contract-level behavior testable without game details.
 */
object TestGame {

    val id = GameId("testgame")
    val variant = VariantId("testgame/v1")
    val date = LocalDate.of(2026, 10, 31)

    // 1x1 board whose single move completes it.
    data class State(val solved: Boolean = false)
    data class PlayMove(val cell: Int) : Move
    data class PlayHint(val text: String) : Hint
    data class Session(val moveCount: Int = 0, val done: Boolean = false) : GameSession
    data class Ref(
        val label: String,
        override val gameId: GameId = TestGame.id,
        override val date: LocalDate = TestGame.date,
        override val puzzleNumber: Int? = null,
    ) : PuzzleRef

    /** The ref [plugin]'s `refOf` builds: any [PuzzleKey], daily or custom. */
    data class KeyRef(override val key: PuzzleKey, override val gameId: GameId = TestGame.id) : PuzzleRef
    data class Model(val solved: Boolean, val lastVerdict: MoveVerdict? = null) : RendererModel

    object Definition : GameDefinition {
        override val id = TestGame.id
        override val displayName = "Test Game"
        override val cadence: Cadence = Cadence.Daily(ZoneId.of("UTC"))
        private val payload = ByteArray(0)

        override fun decode(bytes: ByteArray): DecodeResult<Puzzle> =
            DecodeResult.Ok(Puzzle(id, variant, 1, bytes))

        override fun encode(puzzle: Puzzle): ByteArray {
            check(puzzle.gameId == id) { "foreign envelope: ${puzzle.gameId}" }
            return puzzle.payload
        }

        fun blank(): Puzzle = Puzzle(id, variant, 1, payload)
    }

    object Rules : GameRules<State> {
        override fun validate(move: Move, state: State): MoveVerdict = when (move) {
            is PlayMove -> if (state.solved) Invalid else Valid
            else -> Invalid
        }

        override fun isComplete(state: State): Boolean = state.solved

        override fun hint(state: State): Hint = PlayHint("tap the cell")
    }

    data object Valid : MoveVerdict
    data object Invalid : MoveVerdict

    object SessionCodec : GameSessionCodec<Session> {
        override fun serialize(session: Session): ByteArray =
            byteArrayOf(1, if (session.done) 1 else 0, session.moveCount.toByte())

        override fun deserialize(bytes: ByteArray): DecodeResult<Session> =
            if (bytes.size == 3 && bytes[0].toInt() == 1) {
                DecodeResult.Ok(Session(moveCount = bytes[2].toInt(), done = bytes[1].toInt() == 1))
            } else {
                DecodeResult.Err(DecodeError.Malformed("bad session blob"))
            }
    }

    object Score : ScoreModel<Session> {
        override fun completion(session: Session): CompletionRecord? =
            if (session.done) {
                CompletionRecord(
                    puzzleRef = Ref("t-$date"),
                    time = 1_000L,
                    hints = session.moveCount,
                    stats = RecordStats(1, "{}"),
                )
            } else {
                null
            }
    }

    /** Rules state derived from a session — trivially: the session knows if it is solved. */
    fun stateOf(session: Session): State = State(solved = session.done)

    /** Fresh session for the game's own envelope; typed rejection for a foreign one. */
    fun initialSession(puzzle: Puzzle): DecodeResult<Session> =
        if (puzzle.gameId == id) {
            DecodeResult.Ok(Session())
        } else {
            DecodeResult.Err(DecodeError.Malformed("foreign envelope: ${puzzle.gameId}"))
        }

    fun plugin(): GamePlugin<State, Session, Model> = GamePlugin(
        Definition,
        Rules,
        SessionCodec,
        Score,
        stateOf = { session -> stateOf(session) },
        initialSession = { puzzle -> initialSession(puzzle) },
        refOf = { key -> KeyRef(key) },
        replaySession = { cached, _ -> DecodeResult.Ok(SessionBlob.encode(Definition.id, cached.payload)) },
    ) { state, _, lastVerdict -> Model(state.solved, lastVerdict) }
}
