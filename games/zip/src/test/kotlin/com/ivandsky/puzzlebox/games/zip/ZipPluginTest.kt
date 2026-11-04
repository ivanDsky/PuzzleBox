package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.game.Cadence
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GameDefinition
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRules
import com.ivandsky.puzzlebox.game.GameSession
import com.ivandsky.puzzlebox.game.GameSessionCodec
import com.ivandsky.puzzlebox.game.Hint
import com.ivandsky.puzzlebox.game.Move
import com.ivandsky.puzzlebox.game.MoveVerdict
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.RendererModel
import com.ivandsky.puzzlebox.game.ScoreModel
import com.ivandsky.puzzlebox.games.zip.codec.ZipCodec
import com.ivandsky.puzzlebox.games.zip.codec.ZipSessionCodec
import com.ivandsky.puzzlebox.games.zip.codec.ZipWireParser
import com.ivandsky.puzzlebox.games.zip.engine.ZipMove
import com.ivandsky.puzzlebox.games.zip.engine.ZipRules
import com.ivandsky.puzzlebox.games.zip.engine.ZipSolver
import com.ivandsky.puzzlebox.games.zip.engine.ZipState
import com.ivandsky.puzzlebox.games.zip.engine.ZipValidation
import com.ivandsky.puzzlebox.games.zip.render.ZipRendererModel
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module

/** ZipDefinition contract surface + the full A16 recipe demo on the production fixture. */
class ZipPluginTest {

    private val bytes = ZipCodec.encode(ZipTestSupport.n5Puzzle())

    @Test
    fun `definition identity pins the daily cadence`() {
        assertEquals(GameId("zip"), ZipDefinition.id)
        assertEquals("Zip", ZipDefinition.displayName)
        assertEquals(
            com.ivandsky.puzzlebox.game.Cadence.Daily(java.time.ZoneId.of("America/Los_Angeles")),
            ZipDefinition.cadence,
        )
    }

    /** §4: decode wraps the ORIGINAL bytes — storage stays byte-exact (ADR-0004). */
    @Test
    fun `decode wraps original bytes in the envelope`() {
        val puzzle = (ZipDefinition.decode(bytes) as DecodeResult.Ok).value
        assertEquals(GameId("zip"), puzzle.gameId)
        assertEquals(ZipTestSupport.variantOf(5).id, puzzle.variant)
        assertEquals(ZipCodec.VERSION, puzzle.formatVersion)
        assertArrayEquals(bytes, puzzle.payload)
    }

    @Test
    fun `decode errors pass through untouched`() {
        val poisoned = bytes.clone().also { it[0] = 9 }
        val error = (ZipDefinition.decode(poisoned) as DecodeResult.Err).error
        assertTrue(error is com.ivandsky.puzzlebox.game.DecodeError.UnsupportedVersion)
    }

    @Test
    fun `encode round trips through the envelope canonically`() {
        val puzzle = decoded(bytes)
        assertArrayEquals(bytes, ZipDefinition.encode(puzzle))
    }

    @Test
    fun `encode refuses foreign games and undecodable payloads`() {
        assertThrows(IllegalArgumentException::class.java) {
            ZipDefinition.encode(
                Puzzle(GameId("queens"), ZipTestSupport.variantOf(5).id, ZipCodec.VERSION, bytes),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ZipDefinition.encode(decoded(bytes).let { Puzzle(it.gameId, it.variant, it.formatVersion, byteArrayOf(1, 2, 3)) })
        }
    }

    private fun decoded(bytes: ByteArray): Puzzle =
        when (val result = ZipDefinition.decode(bytes)) {
            is DecodeResult.Ok -> result.value
            is DecodeResult.Err -> throw AssertionError("decode failed: ${result.error}")
        }

    /**
     * A16 recipe, end to end on the captured production payload: decode -> verify shipped
     * solution -> agree with the solver (unique, and equal to shipped) -> THEN trusted.
     */
    @Test
    fun `A16 recipe - fixture payload passes the trust gate`() {
        val fixture = ZipPluginTest::class.java.getResourceAsStream("/fixtures/zip-daily-592.json")!!
            .readBytes().decodeToString()
        val daily = (ZipWireParser.parse(fixture, LocalDate.of(2026, 10, 30), "592") as DecodeResult.Ok).value
        val shipped = daily.puzzle.solution!!

        // gate 1: the shipped path satisfies I1-I5 + I8
        assertTrue(ZipRules.verifyPath(daily.puzzle, shipped))
        // gate 2: the rules engine also accepts it move by move
        val rules = ZipRules
        var state = ZipState(daily.puzzle, emptyList())
        shipped.forEach { cell ->
            val verdict = rules.validateMove(ZipMove.Extend(cell), state)
            assertTrue("move to $cell rejected: $verdict", verdict is ZipValidation.Valid)
            state = state.copy(path = state.path + cell)
        }
        assertTrue(rules.isComplete(state))
        // gate 3: the solver agrees the board is unique and finds the same path
        val solver = ZipSolver()
        assertEquals(1, solver.countSolutions(daily.puzzle))
        assertEquals(shipped, solver.solve(daily.puzzle))
    }

    /** Plugin wiring carries all six contracts (A16 §2) and koin exposes it generically. */
    @Test
    fun `plugin carries every contract and registers in koin`() {
        assertEquals(ZipDefinition, zipPlugin.definition)
        assertEquals(ZipSessionCodec, zipPlugin.session)

        // stateOf: session -> rules state carries the decoded puzzle + trace
        val puzzle = ZipTestSupport.n5Puzzle()
        val session = ZipSession(ref = null, puzzle = puzzle, path = ZipTestSupport.path(0, 1))
        assertEquals(ZipState(puzzle, ZipTestSupport.path(0, 1)), zipPlugin.stateOf(session))

        // initialSession: envelope -> fresh paused session over the decoded board
        val fresh = (zipPlugin.initialSession(decoded(bytes)) as DecodeResult.Ok).value
        assertEquals(ZipSession(ref = null, puzzle = puzzle), fresh)

        // refOf: persisted identity -> Zip's typed ref (storage round-trips refs generically)
        val day = LocalDate.of(2026, 10, 31)
        assertEquals(ZipPuzzleRef(day, 592), zipPlugin.refOf(day, 592))
        assertEquals(ZipPuzzleRef(day, null), zipPlugin.refOf(day, null))

        // rendererModel: rejected verdict -> rejection; valid/absent verdict -> none
        val state = ZipState(puzzle, ZipTestSupport.path(0))
        assertNull(zipPlugin.rendererModel(state, null, null).rejection)
        assertNull(zipPlugin.rendererModel(state, null, ZipValidation.Valid).rejection)
        assertEquals(
            ZipRendererModel.RejectedMove(Cell(6).idx, ZipValidation.Reason.CellAlreadyInPath),
            zipPlugin.rendererModel(
                state,
                null,
                ZipValidation.Invalid(ZipValidation.Reason.CellAlreadyInPath, Cell(6)),
            ).rejection,
        )

        val transport = com.ivandsky.puzzlebox.game.FeedTransport {
            com.ivandsky.puzzlebox.game.FetchResult.Err(com.ivandsky.puzzlebox.game.FetchError.Unavailable())
        }
        org.koin.core.context.GlobalContext.startKoin {
            modules(zipModule, module { single { transport } })
        }
        try {
            // the id qualifier is resolvable...
            assertEquals(
                zipPlugin,
                org.koin.core.context.GlobalContext.get()
                    .get<GamePlugin<*, *, *>>(qualifier(ZipDefinition.id.value)),
            )
            // ...and getAll still collects the qualified definition (F2 §1)
            assertEquals(
                listOf<GamePlugin<*, *, *>>(zipPlugin),
                org.koin.core.context.GlobalContext.get().getAll<GamePlugin<*, *, *>>(),
            )
            // ...and the module contributes Zip's daily source to the generic stack
            assertEquals(
                listOf(ZipDefinition.id),
                org.koin.core.context.GlobalContext.get()
                    .getAll<com.ivandsky.puzzlebox.game.DailySource>().map { it.gameId },
            )
        } finally {
            org.koin.core.context.GlobalContext.stopKoin()
        }
    }

    /** P1: a second game's registration must not overwrite Zip — getAll returns BOTH plugins. */
    @Test
    fun `zip registration survives a second game plugin`() {
        org.koin.core.context.GlobalContext.startKoin { modules(zipModule, otherGameModule) }
        try {
            val plugins = org.koin.core.context.GlobalContext.get().getAll<GamePlugin<*, *, *>>()
            assertEquals(2, plugins.size)
            assertTrue("zip plugin missing from getAll", zipPlugin in plugins)
            assertTrue("other plugin missing from getAll", otherPlugin in plugins)
            // each qualified definition stays individually addressable
            assertEquals(
                zipPlugin,
                org.koin.core.context.GlobalContext.get()
                    .get<GamePlugin<*, *, *>>(qualifier(ZipDefinition.id.value)),
            )
        } finally {
            org.koin.core.context.GlobalContext.stopKoin()
        }
    }
}

/** A second, minimal game plugin: proves qualified registrations coexist under getAll (P1). */
private val otherPlugin: GamePlugin<OtherGameState, OtherGameSession, OtherRendererModel> =
    GamePlugin(
        definition = OtherGameDefinition,
        rules = OtherGameRules,
        session = OtherGameSessionCodec,
        score = OtherGameScore,
        stateOf = { OtherGameState },
        initialSession = { DecodeResult.Err(DecodeError.Malformed("no payload")) },
        refOf = { _, _ -> error("no refs") },
        replaySession = { _, _, _ -> DecodeResult.Err(DecodeError.Malformed("no payload")) },
        rendererModel = { _, _, _ -> OtherRendererModel },
    )

private val otherGameModule = module {
    single(qualifier = qualifier(OtherGameDefinition.id.value)) { otherPlugin }
}

private object OtherGameDefinition : GameDefinition {
    override val id = GameId("other")
    override val displayName = "Other"
    override val cadence: Cadence = Cadence.Daily(java.time.ZoneId.of("UTC"))
    override fun decode(bytes: ByteArray): DecodeResult<Puzzle> = DecodeResult.Err(DecodeError.Malformed("nope"))
    override fun encode(puzzle: Puzzle): ByteArray = throw IllegalArgumentException("not encodable")
}

private object OtherGameState

private object OtherGameSession : GameSession

private object OtherGameRules : GameRules<OtherGameState> {
    override fun validate(move: Move, state: OtherGameState): MoveVerdict = OtherGameVerdict
    override fun isComplete(state: OtherGameState): Boolean = false
    override fun hint(state: OtherGameState): Hint = OtherGameHint
}

private object OtherGameVerdict : MoveVerdict

private object OtherGameHint : Hint

private object OtherGameSessionCodec : GameSessionCodec<OtherGameSession> {
    override fun serialize(session: OtherGameSession): ByteArray = ByteArray(0)
    override fun deserialize(bytes: ByteArray): DecodeResult<OtherGameSession> =
        DecodeResult.Err(DecodeError.Malformed("nope"))
}

private object OtherGameScore : ScoreModel<OtherGameSession> {
    override fun completion(session: OtherGameSession): CompletionRecord? = null
}

private object OtherRendererModel : RendererModel
