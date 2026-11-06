package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The one best formatter: time alone time-first, hints leading the time hints-first. */
class BestScoreTextTest {

    private val time = formatDuration(252_000L)

    @Test
    fun `time-first reads the time alone, whatever the hints`() {
        assertEquals(time, bestScoreText(BestScore(252_000L, hints = 3), RankOrder.TimeThenHints))
    }

    @Test
    fun `hints-first leads with the hints and ends with the time`() {
        val texts = (0..2).map { bestScoreText(BestScore(252_000L, hints = it), RankOrder.HintsThenTime) }

        texts.forEach { assertTrue(it, it.endsWith(" · $time")) }
        assertEquals(texts.size, texts.toSet().size)
        // A hint-free best names no count; a hinted one leads with its count.
        assertTrue(texts[0], !texts[0].first().isDigit())
        assertTrue(texts[2], texts[2].startsWith("2"))
        assertNotEquals(texts[0], texts[1])
    }
}
