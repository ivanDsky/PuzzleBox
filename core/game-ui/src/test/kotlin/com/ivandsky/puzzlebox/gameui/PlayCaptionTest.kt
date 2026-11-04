package com.ivandsky.puzzlebox.gameui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A7 / A18: core resolves the caption once — violation > rejection > hint. */
class PlayCaptionTest {

    private fun feedback(violation: String? = null, rejection: String? = null) = object : PlayFeedback {
        override val violation = violation
        override val rejection = rejection
    }

    private fun hints(caption: String?) = object : PlayHints {
        override val hintsUsed = 0
        override val hintEnabled = true
        override val hintCaption = caption
        override fun requestHint() = Unit
    }

    @Test
    fun `no seams means no caption`() {
        assertNull(playCaption(feedback = null, hints = null))
    }

    @Test
    fun `empty seams mean no caption`() {
        assertNull(playCaption(feedback(), hints(null)))
    }

    @Test
    fun `hint shows alone`() {
        assertEquals("hint", playCaption(feedback = null, hints = hints("hint")))
        assertEquals("hint", playCaption(feedback(), hints("hint")))
    }

    @Test
    fun `rejection beats hint`() {
        assertEquals("rejected", playCaption(feedback(rejection = "rejected"), hints("hint")))
    }

    @Test
    fun `violation beats rejection and hint`() {
        assertEquals(
            "violation",
            playCaption(feedback(violation = "violation", rejection = "rejected"), hints("hint")),
        )
    }

    @Test
    fun `violation shows without hints seam`() {
        assertEquals("violation", playCaption(feedback(violation = "violation"), hints = null))
    }
}
