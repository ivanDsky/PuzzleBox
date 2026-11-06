package com.ivandsky.puzzlebox.data.model

import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.Ref
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The composite record identity (gameId + PuzzleKey, daily or custom) round-trips through one key
 * form, rebuilt by the owning game's `refOf` from the [com.ivandsky.puzzlebox.game.GameRegistry].
 */
class PuzzleRefKeysTest {

    private val day = LocalDate.of(2026, 10, 31)
    private val registry = TestGames.registry(listOf(alpha, beta))

    @Test
    fun `numbered ref encodes and decodes symmetrically`() {
        val ref = Ref(alpha, PuzzleKey.Daily(day, 592))
        assertEquals("alpha/2026-10-31/592", PuzzleRefKeys.encode(ref))
        assertEquals(ref, PuzzleRefKeys.decode("alpha/2026-10-31/592", registry))
    }

    @Test
    fun `unnumbered ref encodes and decodes symmetrically`() {
        val ref = Ref(alpha, PuzzleKey.Daily(day, puzzleNumber = null))
        assertEquals("alpha/2026-10-31", PuzzleRefKeys.encode(ref))
        assertEquals(ref, PuzzleRefKeys.decode("alpha/2026-10-31", registry))
    }

    @Test
    fun `each registered game decodes under its own game id`() {
        val numbered = Ref(beta, PuzzleKey.Daily(day, 42))
        assertEquals("beta/2026-10-31/42", PuzzleRefKeys.encode(numbered))
        assertEquals(numbered, PuzzleRefKeys.decode("beta/2026-10-31/42", registry))

        val unnumbered = Ref(beta, PuzzleKey.Daily(day, puzzleNumber = null))
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

    @Test
    fun `custom ref encodes under the custom namespace, never its game's daily prefix`() {
        val key = PuzzleRefKeys.encode(CustomRef(GameId("queens"), PuzzleKey.Custom("K7Q2MX")))
        assertEquals("custom/queens/K7Q2MX", key)
        assertFalse(key.startsWith("queens/"))
    }

    @Test
    fun `custom key decodes back to the authoring game's typed ref`() {
        val ref = Ref(alpha, PuzzleKey.Custom("K7Q2MX"))
        assertEquals("custom/alpha/K7Q2MX", PuzzleRefKeys.encode(ref))
        assertEquals(ref, PuzzleRefKeys.decode("custom/alpha/K7Q2MX", registry))
    }

    @Test
    fun `custom keys for an unregistered game or of the wrong shape decode to null`() {
        assertNull(PuzzleRefKeys.decode("custom/strands/K7Q2MX", registry))
        assertNull(PuzzleRefKeys.decode("custom/alpha", registry))
        assertNull(PuzzleRefKeys.decode("custom/alpha/", registry))
        assertNull(PuzzleRefKeys.decode("custom/alpha/K7Q2MX/extra", registry))
    }

    @Test
    fun `daily key form is unchanged for a ref that overrides key`() {
        val ref = object : PuzzleRef {
            override val gameId = GameId("queens")
            override val key = PuzzleKey.Daily(LocalDate.of(2026, 11, 6), 918)
        }
        assertEquals("queens/2026-11-06/918", PuzzleRefKeys.encode(ref))
    }

    private data class CustomRef(override val gameId: GameId, override val key: PuzzleKey.Custom) : PuzzleRef
}
