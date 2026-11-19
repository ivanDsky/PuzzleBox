package com.ivandsky.puzzlebox.tools.puzzlecli

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The checkpoint file's format: round trip, torn tails and refusals. */
class IndexCheckpointTest {

    private val game = GameId("queens")

    private fun parsed(text: String): IndexCheckpoint = (IndexCheckpoint.parse(text) as DecodeResult.Ok).value

    @Test
    fun `settlements round trip through the text`() {
        val ok = Settlement.Ok(mapOf(TechniqueId("pairs") to 2, TechniqueId("singles") to 4))
        val skip = Settlement.Skip(SkipKind.NotFound, "outside #1..#9")
        val checkpoint = IndexCheckpoint.empty().with(game, 7, skip).with(game, 3, ok)
        val again = parsed(checkpoint.text())
        assertEquals(ok, again.settlement(game, 3))
        assertEquals(skip, again.settlement(game, 7))
        assertNull(again.settlement(game, 4))
        assertNull(again.tornLine)
        assertEquals(checkpoint.text(), again.text())
        assertTrue(checkpoint.text(), "queens 3 ok pairs=2 singles=4\n" in checkpoint.text())
    }

    @Test
    fun `a skip detail is one line`() {
        val line = IndexCheckpoint.line(game, 1, Settlement.Skip(SkipKind.Untrusted, "a\r\n  b\nc "))
        assertEquals("queens 1 skip Untrusted a b c", line)
    }

    @Test
    fun `a newer header version is unsupported`() {
        val result = IndexCheckpoint.parse("puzzle-cli-index-checkpoint 2\n")
        assertEquals(DecodeResult.Err(DecodeError.UnsupportedVersion(2, 1)), result)
    }
}
