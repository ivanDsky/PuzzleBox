package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GameId
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.zip.codec.ZipCodec
import com.ivandsky.puzzlebox.games.zip.codec.ZipSessionCodec
import com.ivandsky.puzzlebox.games.zip.codec.ZipWireParser
import com.ivandsky.puzzlebox.games.zip.engine.ZipRules
import com.ivandsky.puzzlebox.games.zip.engine.ZipSolver
import java.time.LocalDate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

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
        val rules = com.ivandsky.puzzlebox.games.zip.engine.ZipRules()
        var state = com.ivandsky.puzzlebox.games.zip.engine.ZipState(daily.puzzle, emptyList())
        shipped.forEach { cell ->
            val verdict = rules.validateMove(
                com.ivandsky.puzzlebox.games.zip.engine.ZipMove.Extend(cell),
                state,
            )
            assertTrue("move to $cell rejected: $verdict", verdict is com.ivandsky.puzzlebox.games.zip.engine.ZipValidation.Valid)
            state = state.copy(path = state.path + cell)
        }
        assertTrue(rules.isComplete(state))
        // gate 3: the solver agrees the board is unique and finds the same path
        val solver = ZipSolver()
        assertEquals(1, solver.countSolutions(daily.puzzle))
        assertEquals(shipped, solver.solve(daily.puzzle))
    }

    /** Plugin wiring carries all five contracts (A16 §2) and koin exposes it generically. */
    @Test
    fun `plugin carries every contract and registers in koin`() {
        assertEquals(ZipDefinition, zipPlugin.definition)
        assertEquals(ZipSessionCodec, zipPlugin.session)
        org.koin.core.context.GlobalContext.startKoin { modules(zipModule) }
        try {
            val plugins = org.koin.core.context.GlobalContext.get().getAll<com.ivandsky.puzzlebox.game.GamePlugin<*, *, *>>()
            assertEquals(listOf<com.ivandsky.puzzlebox.game.GamePlugin<*, *, *>>(zipPlugin), plugins.toList())
        } finally {
            org.koin.core.context.GlobalContext.stopKoin()
        }
    }
}
