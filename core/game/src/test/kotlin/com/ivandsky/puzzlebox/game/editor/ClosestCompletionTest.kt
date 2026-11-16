package com.ivandsky.puzzlebox.game.editor

import com.ivandsky.puzzlebox.game.editor.Difficulty.Level.Easy
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level.Expert
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level.Hard
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level.Medium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a completer returns when it aims for a level (90): a hit, else the closest board, and GaveUp only with no board at all. */
class ClosestCompletionTest {

    @Test
    fun `with nothing offered it gave up`() {
        assertEquals(Completion.GaveUp, ClosestCompletion<String>(Hard).completion())
    }

    @Test
    fun `a board at the target is a hit and is the completion`() {
        val closest = ClosestCompletion<String>(Hard)

        assertFalse(closest.offer("medium", Medium))
        assertTrue(closest.offer("hard", Hard))
        assertEquals(Completion.Done("hard"), closest.completion())
    }

    @Test
    fun `a miss returns the closest level found, never GaveUp`() {
        val closest = ClosestCompletion<String>(Expert)

        closest.offer("easy", Easy)
        closest.offer("hard", Hard)
        closest.offer("medium", Medium)

        assertEquals(Completion.Done("hard"), closest.completion())
    }

    @Test
    fun `of two boards equally far the first found is kept`() {
        val closest = ClosestCompletion<String>(Medium)

        closest.offer("easy", Easy)
        closest.offer("hard", Hard)

        assertEquals(Completion.Done("easy"), closest.completion())
    }

    @Test
    fun `without a target the first board is a hit`() {
        val closest = ClosestCompletion<String>(target = null)

        assertTrue(closest.offer("any", Expert))
        assertEquals(Completion.Done("any"), closest.completion())
    }

    @Test
    fun `an ungraded board is kept only until a graded one arrives`() {
        val closest = ClosestCompletion<String>(Easy)

        assertFalse(closest.offer("ungraded", null))
        assertEquals(Completion.Done("ungraded"), closest.completion())
        closest.offer("expert", Expert)

        assertEquals(Completion.Done("expert"), closest.completion())
    }
}
