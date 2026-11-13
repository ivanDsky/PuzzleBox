package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.history.ranks.RanksSelection.Window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The top bar names only what was tuned away from Global and Day. */
class RanksTopBarTest {

    @Test
    fun `the defaults have no label`() = assertNull(tuneLabel(RankScope.Global, Window.Day))

    @Test
    fun `a non-default choice is named, both joined`() {
        assertEquals("Friends", tuneLabel(RankScope.Friends, Window.Day))
        assertEquals("Week", tuneLabel(RankScope.Global, Window.Week))
        assertEquals("Friends · Month", tuneLabel(RankScope.Friends, Window.Month))
    }
}
