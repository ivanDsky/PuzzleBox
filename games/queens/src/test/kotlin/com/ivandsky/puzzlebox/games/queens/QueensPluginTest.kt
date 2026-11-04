package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.game.Cadence
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.Move
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.codec.QueensSessionCodec
import com.ivandsky.puzzlebox.games.queens.engine.hints.QueensHint
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRuleId
import com.ivandsky.puzzlebox.games.queens.render.QueensRendererModel
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
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

        val placed = session.withQueenPlaced(Cell(0), Instant.now())
        assertEquals(QueensState(puzzle, setOf(Cell(0))), queensPlugin.stateOf(placed))

        val fresh = (queensPlugin.initialSession((QueensDefinition.decode(bytes) as DecodeResult.Ok).value) as DecodeResult.Ok).value
        assertEquals(QueensSession(ref = null, puzzle = puzzle), fresh)
        // foreign envelopes are rejected, never guessed
        assertTrue(queensPlugin.initialSession(Puzzle(GameId("zip"), queensVariantId(8), 1, bytes)) is DecodeResult.Err)
        // refOf rebuilds the typed fetch-time identity from stored columns (ADR-0004).
        val day = LocalDate.of(2026, 11, 2)
        assertEquals(QueensPuzzleRef(day, 12), queensPlugin.refOf(day, 12))
        assertEquals(QueensPuzzleRef(day, null), queensPlugin.refOf(day, null))

        val model = queensPlugin.rendererModel(QueensState(puzzle, setOf(Cell(0), Cell(9))), QueensHint.None, null)
        assertEquals(8, model.n)
        assertEquals(setOf(0, 9), model.queens)
        assertEquals(puzzle.regions, model.regions)
        assertEquals(QueensRendererModel.Status.PLAYING, model.status)
    }

    /** Q-4: the plugin exposes the real rules verdict, not the skeleton rejection. */
    @Test
    fun `rules validate placements through the engine pipeline`() {
        val state = QueensState(puzzle, setOf(Cell(0)))
        // (2,2) shares neither row 0, column 0, nor region ((2+2)%8=4 vs 0), and does
        // not touch (0,0) — a legal placement.
        assertEquals(QueensValidation.Valid, QueensGameRules.validate(QueensPlayMove.Place(Cell(18)), state))
        // (0,1) shares row 0 with (0,0) → RowExclusivity (pipeline order: cheap first).
        val invalid = QueensGameRules.validate(QueensPlayMove.Place(Cell(1)), state) as QueensValidation.Invalid
        assertEquals(QueensRuleId.RowExclusivity, invalid.rule)
        // structural: occupied cell, remove from an empty cell, foreign move.
        assertEquals(QueensValidation.Invalid(), QueensGameRules.validate(QueensPlayMove.Place(Cell(0)), state))
        assertEquals(QueensValidation.Invalid(), QueensGameRules.validate(QueensPlayMove.Remove(Cell(3)), state))
        assertTrue(QueensGameRules.validate(object : Move {}, state) is QueensValidation.Invalid)
    }

    @Test
    fun `rules completeness and hints are real`() {
        assertEquals(false, QueensGameRules.isComplete(QueensState(puzzle, setOf(Cell(0)))))
        // A rule-clean 8×8 placement for regions = row: one queen per row (hence one
        // per region), distinct columns, no adjacent diagonals.
        val rowRegions = (0 until 64).map { it / 8 }
        val rowPuzzle = QueensPuzzle(8, rowRegions, solution = null)
        val cleanCols = listOf(1, 3, 5, 7, 0, 2, 4, 6)
        val clean = cleanCols.mapIndexed { row, col -> Cell(row * 8 + col) }.toSet()
        assertEquals(true, QueensGameRules.isComplete(QueensState(rowPuzzle, clean)))
        assertTrue(QueensGameRules.hint(QueensState(puzzle)) is QueensHint)
    }

    /** DoD: the DI-graph sees QueensPlugin registered; getAll collects it generically
     *  alongside a second qualified registration (P1 coexistence, zip precedent). */
    @Test
    fun `plugin registers in koin and survives a second game plugin`() {
        val transport = com.ivandsky.puzzlebox.game.FeedTransport {
            com.ivandsky.puzzlebox.game.FetchResult.Err(com.ivandsky.puzzlebox.game.FetchError.Unavailable())
        }
        org.koin.core.context.GlobalContext.startKoin {
            modules(queensModule, otherGameModule, module { single { transport } })
        }
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
            val sources = org.koin.core.context.GlobalContext.get()
                .getAll<com.ivandsky.puzzlebox.game.DailySource>()
            assertEquals(listOf(QueensDefinition.id), sources.map { it.gameId })
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
        refOf = { _, _ -> error("no refs") },
        replaySession = { _, _, _ -> DecodeResult.Err(DecodeError.Malformed("no payload")) },
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
