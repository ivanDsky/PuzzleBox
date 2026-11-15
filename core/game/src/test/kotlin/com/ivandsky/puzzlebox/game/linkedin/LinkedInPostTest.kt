package com.ivandsky.puzzlebox.game.linkedin

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkedInPostTest {
    private fun post(hintUsed: Boolean, mistakeFree: Boolean) = LinkedInPost(
        gamePath = "games/zip/",
        gameTypeId = 6,
        puzzleNumber = 608,
        timeMs = 61_000,
        hintUsed = hintUsed,
        play = LinkedInPlay(emptyMap(), emptyMap(), mistakeFree),
    )

    @Test
    fun `flawless needs no hint and a mistake-free play`() {
        assertEquals(true, post(hintUsed = false, mistakeFree = true).flawless)
        assertEquals(false, post(hintUsed = true, mistakeFree = true).flawless)
        assertEquals(false, post(hintUsed = false, mistakeFree = false).flawless)
        assertEquals(false, post(hintUsed = true, mistakeFree = false).flawless)
    }
}
