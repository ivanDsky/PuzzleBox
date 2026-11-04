package com.ivandsky.puzzlebox.data.model

import com.ivandsky.puzzlebox.data.testing.TestGames
import com.ivandsky.puzzlebox.data.testing.TestGames.Ref
import com.ivandsky.puzzlebox.data.testing.TestGames.alpha
import com.ivandsky.puzzlebox.data.testing.TestGames.beta
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The composite record identity (gameId + day/puzzleNumber) round-trips through one key
 * form, rebuilt by the owning game's `refOf` from the [com.ivandsky.puzzlebox.game.GameRegistry].
 */
class PuzzleRefKeysTest {

    private val day = LocalDate.of(2026, 10, 31)
    private val registry = TestGames.registry(listOf(alpha, beta))

    @Test
    fun `numbered ref encodes and decodes symmetrically`() {
        val ref = Ref(alpha, day, 592)
        assertEquals("alpha/2026-10-31/592", PuzzleRefKeys.encode(ref))
        assertEquals(ref, PuzzleRefKeys.decode("alpha/2026-10-31/592", registry))
    }

    @Test
    fun `unnumbered ref encodes and decodes symmetrically`() {
        val ref = Ref(alpha, day, puzzleNumber = null)
        assertEquals("alpha/2026-10-31", PuzzleRefKeys.encode(ref))
        assertEquals(ref, PuzzleRefKeys.decode("alpha/2026-10-31", registry))
    }

    @Test
    fun `each registered game decodes under its own game id`() {
        val numbered = Ref(beta, day, 42)
        assertEquals("beta/2026-10-31/42", PuzzleRefKeys.encode(numbered))
        assertEquals(numbered, PuzzleRefKeys.decode("beta/2026-10-31/42", registry))

        val unnumbered = Ref(beta, day, puzzleNumber = null)
        assertEquals("beta/2026-10-31", PuzzleRefKeys.encode(unnumbered))
        assertEquals(unnumbered, PuzzleRefKeys.decode("beta/2026-10-31", registry))
    }

    @Test
    fun `keys no registered game authored decode to null`() {
        assertNull(PuzzleRefKeys.decode("strands/2026-10-31/12", registry))
        assertNull(PuzzleRefKeys.decode("alpha/not-a-date", registry))
        assertNull(PuzzleRefKeys.decode("alpha/2026-10-31/not-a-number", registry))
        assertNull(PuzzleRefKeys.decode("alpha/2026-10-31/592/extra", registry))
        assertNull(PuzzleRefKeys.decode("beta/not-a-date", registry))
    }

    @Test
    fun `a game dropped from the build no longer decodes its keys`() {
        assertNull(PuzzleRefKeys.decode("beta/2026-10-31/42", TestGames.registry(listOf(alpha))))
    }
}
