package com.ivandsky.puzzlebox.testing.game

import com.ivandsky.puzzlebox.game.Cadence
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.DailyFetch
import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.GameDefinition
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.GameRules
import com.ivandsky.puzzlebox.game.GameSession
import com.ivandsky.puzzlebox.game.GameSessionCodec
import com.ivandsky.puzzlebox.game.Hint
import com.ivandsky.puzzlebox.game.Move
import com.ivandsky.puzzlebox.game.MoveVerdict
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RendererModel
import com.ivandsky.puzzlebox.game.ScoreModel
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import java.time.ZoneId

/**
 * Stand-in games for modules that must never see a real game (core:data has no games:*
 * edge): they register under arbitrary ids so storage, history and the daily repository
 * are exercised through [GameRegistry] exactly as production wires them. A payload
 * decodes only when it starts with [TestGames.MAGIC], so corrupted-row paths stay testable.
 */
object TestGames {

    val MAGIC: Byte = 0x42

    val alpha = GameId("alpha")
    val beta = GameId("beta")

    /** A game's typed ref, round-trippable through [com.ivandsky.puzzlebox.data.model.PuzzleRefKeys]. */
    data class Ref(
        override val gameId: GameId,
        override val date: LocalDate,
        override val puzzleNumber: Int? = null,
    ) : PuzzleRef

    fun variant(game: GameId) = VariantId("${game.value}/v1")

    /** A decodable envelope for [game]; [tag] makes payloads distinguishable. */
    fun puzzle(game: GameId, tag: Byte = 0): Puzzle = Puzzle(game, variant(game), 1, byteArrayOf(MAGIC, tag))

    fun definition(game: GameId): GameDefinition = object : GameDefinition {
        override val id = game
        override val displayName = game.value.replaceFirstChar(Char::uppercase)
        override val cadence: Cadence = Cadence.Daily(ZoneId.of("UTC"))

        override fun decode(bytes: ByteArray): DecodeResult<Puzzle> =
            if (bytes.isNotEmpty() && bytes[0] == MAGIC) DecodeResult.Ok(Puzzle(game, variant(game), 1, bytes))
            else DecodeResult.Err(DecodeError.Malformed("not a ${game.value} payload"))

        override fun encode(puzzle: Puzzle): ByteArray = puzzle.payload
    }

    fun plugin(game: GameId): GamePlugin<Unit, Session, Model> = GamePlugin(
        definition = definition(game),
        rules = Rules,
        session = SessionCodec,
        score = Score,
        stateOf = { },
        initialSession = { DecodeResult.Ok(Session) },
        refOf = { date, number -> Ref(game, date, number) },
        replaySession = { cached, day, number ->
            if (cached.payload.isNotEmpty() && cached.payload[0] == MAGIC) {
                DecodeResult.Ok(replayBlob(game, cached, day, number))
            } else {
                DecodeResult.Err(DecodeError.Malformed("not a ${game.value} payload"))
            }
        },
        rendererModel = { _, _, _ -> Model },
    )

    /** The replay hand-off [plugin] builds: [game]'s envelope over the payload plus the day's identity. */
    fun replayBlob(game: GameId, cached: Puzzle, day: LocalDate, puzzleNumber: Int?): ByteArray =
        SessionBlob.encode(game, cached.payload + "$day#$puzzleNumber".toByteArray(Charsets.UTF_8))

    fun registry(games: List<GameId>): GameRegistry = GameRegistry(games.map(::plugin))

    object Session : GameSession
    object Model : RendererModel

    private object Rules : GameRules<Unit> {
        override fun validate(move: Move, state: Unit): MoveVerdict = object : MoveVerdict {}
        override fun isComplete(state: Unit): Boolean = false
        override fun hint(state: Unit): Hint = object : Hint {}
    }

    private object SessionCodec : GameSessionCodec<Session> {
        override fun serialize(session: Session): ByteArray = ByteArray(0)
        override fun deserialize(bytes: ByteArray): DecodeResult<Session> = DecodeResult.Ok(Session)
    }

    private object Score : ScoreModel<Session> {
        override fun completion(session: Session): CompletionRecord? = null
    }
}

/**
 * Scriptable [DailySource] for one game: queue the next outcome, record the requested
 * dates. Default fails Unavailable so tests assert the degrade path explicitly.
 */
class FakeDailySource(override val gameId: GameId) : DailySource {

    val requestedDates = mutableListOf<LocalDate>()
    var nextPuzzle: Puzzle? = null
    var nextPuzzleNumber: Int? = null
    var nextError: FetchError = FetchError.Unavailable("FakeDailySource: nothing queued")

    override suspend fun fetchDaily(date: LocalDate): FetchResult<DailyFetch> {
        requestedDates += date
        val puzzle = nextPuzzle ?: return FetchResult.Err(nextError)
        // Mirror the real adapters: the ref is always built from the requested day.
        return FetchResult.Ok(DailyFetch(puzzle, TestGames.Ref(gameId, date, nextPuzzleNumber)))
    }
}
