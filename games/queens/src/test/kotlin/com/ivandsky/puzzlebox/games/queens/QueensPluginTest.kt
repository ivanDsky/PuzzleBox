package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.game.Cadence
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.codec.QueensSessionCodec
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module

/** The plugin contract surface: definition, plugin seams, Koin registration (ADR-0001). */
class QueensPluginTest {

    private val puzzle = QueensPuzzle(
        size = 8,
        regions = (0 until 64).map { (it / 8 + it % 8) % 8 },
        solution = (0 until 8).map { Cell(it * 8 + it) },
    )
    private val bytes = QueensCodec.encode(puzzle)

    @Test
    fun `definition identity pins the daily cadence`() {
        assertEquals(GameId("queens"), QueensDefinition.id)
        assertEquals("Queens", QueensDefinition.displayName)
        assertEquals(Cadence.Daily(ZoneId.of("America/Los_Angeles")), QueensDefinition.cadence)
    }

    /** ADR-0004: decode wraps the ORIGINAL bytes — storage stays byte-exact. */
    @Test
    fun `decode wraps original bytes in the envelope`() {
        val envelope = (QueensDefinition.decode(bytes) as DecodeResult.Ok).value
        assertEquals(GameId("queens"), envelope.gameId)
        assertEquals(queensVariantId(8), envelope.variant)
        assertEquals(QueensCodec.VERSION, envelope.formatVersion)
        assertArrayEquals(bytes, envelope.payload)
    }

    @Test
    fun `decode errors pass through untouched`() {
        val poisoned = bytes.clone().also { it[0] = 9 }
        val error = (QueensDefinition.decode(poisoned) as DecodeResult.Err).error
        assertTrue(error is DecodeError.UnsupportedVersion)
    }

    @Test
    fun `encode round trips through the envelope canonically`() {
        val envelope = (QueensDefinition.decode(bytes) as DecodeResult.Ok).value
        assertArrayEquals(bytes, QueensDefinition.encode(envelope))
    }

    @Test
    fun `encode refuses foreign games and undecodable payloads`() {
        assertThrows(IllegalArgumentException::class.java) {
            QueensDefinition.encode(Puzzle(GameId("zip"), queensVariantId(8), QueensCodec.VERSION, bytes))
        }
        assertThrows(IllegalArgumentException::class.java) {
            QueensDefinition.encode(Puzzle(GameId("queens"), queensVariantId(8), QueensCodec.VERSION, byteArrayOf(1, 2, 3)))
        }
    }

    /** Plugin wiring carries all contracts (ADR-0001 §2) and the skeleton seams behave. */
    @Test
    fun `plugin carries every contract`() {
        assertEquals(QueensDefinition, queensPlugin.definition)
        assertEquals(QueensSessionCodec, queensPlugin.session)

        val session = QueensSession(ref = QueensPuzzleRef(LocalDate.of(2026, 11, 2), 7), puzzle = puzzle)
        assertEquals(QueensState(puzzle), queensPlugin.stateOf(session))

        val fresh = (queensPlugin.initialSession((QueensDefinition.decode(bytes) as DecodeResult.Ok).value) as DecodeResult.Ok).value
        assertEquals(QueensSession(ref = null, puzzle = puzzle), fresh)
        // foreign envelopes are rejected, never guessed
        assertTrue(queensPlugin.initialSession(Puzzle(GameId("zip"), queensVariantId(8), 1, bytes)) is DecodeResult.Err)

        val model = queensPlugin.rendererModel(QueensState(puzzle, setOf(Cell(0), Cell(9))), QueensHint, null)
        assertEquals(8, model.n)
        assertEquals(setOf(0, 9), model.queens)
        assertEquals(puzzle.regions, model.regions)
    }

    /** Skeleton rules are total: foreign/known moves get a typed rejection, never a throw. */
    @Test
    fun `skeleton rules reject every move with a typed verdict`() {
        assertEquals(QueensMoveRejected, QueensRules.validate(QueensMove, QueensState(puzzle)))
        assertEquals(false, QueensRules.isComplete(QueensState(puzzle)))
        assertEquals(QueensHint, QueensRules.hint(QueensState(puzzle)))
    }

    /** DoD: the DI-graph sees QueensPlugin registered; getAll collects it generically
     *  alongside a second qualified registration (P1 coexistence, zip precedent). */
    @Test
    fun `plugin registers in koin and survives a second game plugin`() {
        org.koin.core.context.GlobalContext.startKoin { modules(queensModule, otherGameModule) }
        try {
            assertEquals(
                queensPlugin,
                org.koin.core.context.GlobalContext.get()
                    .get<GamePlugin<*, *, *>>(qualifier(QueensDefinition.id.value)),
            )
            val plugins = org.koin.core.context.GlobalContext.get().getAll<GamePlugin<*, *, *>>()
            assertEquals(2, plugins.size)
            assertTrue("queens plugin missing from getAll", queensPlugin in plugins)
            assertTrue("other plugin missing from getAll", otherPlugin in plugins)
        } finally {
            org.koin.core.context.GlobalContext.stopKoin()
        }
    }
}

/** A second, minimal game plugin: proves qualified registrations coexist under getAll. */
private val otherPlugin: GamePlugin<OtherGameState, com.ivandsky.puzzlebox.game.GameSession, com.ivandsky.puzzlebox.game.RendererModel> =
    GamePlugin(
        definition = OtherGameDefinition,
        rules = OtherGameRules,
        session = OtherGameSessionCodec,
        score = OtherGameScore,
        stateOf = { OtherGameState },
        initialSession = { DecodeResult.Err(DecodeError.Malformed("no payload")) },
        rendererModel = { _, _, _ -> OtherRendererModel },
    )

private val otherGameModule = module {
    single(qualifier = qualifier(OtherGameDefinition.id.value)) { otherPlugin }
}

private object OtherGameDefinition : com.ivandsky.puzzlebox.game.GameDefinition {
    override val id = GameId("other")
    override val displayName = "Other"
    override val cadence: Cadence = Cadence.Daily(ZoneId.of("UTC"))
    override fun decode(bytes: ByteArray): DecodeResult<Puzzle> = DecodeResult.Err(DecodeError.Malformed("nope"))
    override fun encode(puzzle: Puzzle): ByteArray = throw IllegalArgumentException("not encodable")
}

private object OtherGameState

private object OtherGameRules : com.ivandsky.puzzlebox.game.GameRules<OtherGameState> {
    override fun validate(move: com.ivandsky.puzzlebox.game.Move, state: OtherGameState): com.ivandsky.puzzlebox.game.MoveVerdict =
        object : com.ivandsky.puzzlebox.game.MoveVerdict {}

    override fun isComplete(state: OtherGameState): Boolean = false

    override fun hint(state: OtherGameState): com.ivandsky.puzzlebox.game.Hint =
        object : com.ivandsky.puzzlebox.game.Hint {}
}

private object OtherGameSessionCodec : com.ivandsky.puzzlebox.game.GameSessionCodec<com.ivandsky.puzzlebox.game.GameSession> {
    override fun serialize(session: com.ivandsky.puzzlebox.game.GameSession): ByteArray = ByteArray(0)
    override fun deserialize(bytes: ByteArray): DecodeResult<com.ivandsky.puzzlebox.game.GameSession> =
        DecodeResult.Err(DecodeError.Malformed("nope"))
}

private object OtherGameScore : com.ivandsky.puzzlebox.game.ScoreModel<com.ivandsky.puzzlebox.game.GameSession> {
    override fun completion(session: com.ivandsky.puzzlebox.game.GameSession): com.ivandsky.puzzlebox.game.CompletionRecord? = null
}

private object OtherRendererModel : com.ivandsky.puzzlebox.game.RendererModel
