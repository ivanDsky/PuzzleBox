package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.game.RankOrder
import org.junit.Assert.assertEquals
import org.junit.Test

/** One caption per [RankOrder]. */
class RankRuleTextTest {

    @Test
    fun timeThenHintsNamesTimeFirst() {
        assertEquals("Fastest first, then fewer hints", rankRuleText(RankOrder.TimeThenHints))
    }

    @Test
    fun hintsThenTimeNamesHintsFirst() {
        assertEquals("Fewest hints first, then time", rankRuleText(RankOrder.HintsThenTime))
    }
}
