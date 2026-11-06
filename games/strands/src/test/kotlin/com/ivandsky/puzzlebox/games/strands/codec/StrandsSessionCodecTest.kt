package com.ivandsky.puzzlebox.games.strands.codec

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.strands.ActiveHint
import com.ivandsky.puzzlebox.games.strands.HintStage
import com.ivandsky.puzzlebox.games.strands.StrandsFixtures
import com.ivandsky.puzzlebox.games.strands.StrandsNumber
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzleRef
import com.ivandsky.puzzlebox.games.strands.StrandsSession
import com.ivandsky.puzzlebox.games.strands.StrandsState
import com.ivandsky.puzzlebox.games.strands.tinyPuzzle
import java.time.LocalDate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrandsSessionCodecTest {

    private val day = LocalDate.of(2026, 11, 5)
    private val real = StrandsFixtures.puzzle(day)

    private val midGame = StrandsSession(
        ref = StrandsPuzzleRef(day, StrandsNumber.of(day)),
        state = StrandsState(
            puzzle = real,
            found = listOf("WIND", "GIVEMEABREAK", "BREAD"),
            nonTheme = linkedSetOf("RAGE", "DIME", "BRAG", "AHEM"),
            hintsUsed = 2,
            activeHint = ActiveHint("FAITH", HintStage.OrderRevealed),
        ),
        elapsedMs = 987_654_321L,
    )

    private fun roundTrip(session: StrandsSession): StrandsSession =
        StrandsSessionCodec.deserialize(StrandsSessionCodec.serialize(session)).getOrNull()!!

    @Test
    fun `every A30 field round-trips`() {
        val restored = roundTrip(midGame)
        assertEquals(midGame, restored)
        assertEquals(midGame.state.found, restored.state.found)
        assertEquals(midGame.state.nonTheme.toList(), restored.state.nonTheme.toList())
        assertEquals(2, restored.state.hintsUsed)
        assertEquals(ActiveHint("FAITH", HintStage.OrderRevealed), restored.state.activeHint)
        assertEquals(987_654_321L, restored.elapsedMs)
        assertEquals(StrandsPuzzleRef(day, 977), restored.ref)
    }

    @Test
    fun `serialization is canonical`() {
        val bytes = StrandsSessionCodec.serialize(midGame)
        assertArrayEquals(bytes, StrandsSessionCodec.serialize(roundTrip(midGame)))
    }

    @Test
    fun `fresh session without ref or hint round-trips`() {
        val fresh = StrandsSession(ref = null, state = StrandsState(tinyPuzzle))
        assertEquals(fresh, roundTrip(fresh))
    }

    @Test
    fun `ref without a number round-trips`() {
        val session = StrandsSession(ref = StrandsPuzzleRef(day, null), state = StrandsState(tinyPuzzle))
        assertEquals(session, roundTrip(session))
    }

    @Test
    fun `every truncation is malformed`() {
        val tinyState = StrandsState(
            puzzle = tinyPuzzle,
            found = listOf("CAT"),
            nonTheme = setOf("TAT"),
            hintsUsed = 1,
            activeHint = ActiveHint("ATS", HintStage.Outlined),
        )
        val bytes = StrandsSessionCodec.serialize(midGame.copy(state = tinyState))
        for (len in 0 until bytes.size) {
            val result = StrandsSessionCodec.deserialize(bytes.copyOf(len))
            assertTrue("prefix $len: $result", (result as DecodeResult.Err).error is DecodeError.Malformed)
        }
    }

    @Test
    fun `unknown version is unsupported`() {
        val bytes = StrandsSessionCodec.serialize(midGame).also { it[0] = 2 }
        assertEquals(
            DecodeResult.Err(DecodeError.UnsupportedVersion(found = 2, maxKnown = StrandsSessionCodec.VERSION)),
            StrandsSessionCodec.deserialize(bytes),
        )
    }

    @Test
    fun `a found word outside the puzzle is malformed`() {
        val bytes = StrandsSessionCodec.serialize(
            StrandsSession(ref = null, state = StrandsState(tinyPuzzle, found = listOf("TAT"))),
        )
        assertTrue((StrandsSessionCodec.deserialize(bytes) as DecodeResult.Err).error is DecodeError.Malformed)
    }
}
