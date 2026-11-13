package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.games.strands.ActiveHint
import com.ivandsky.puzzlebox.games.strands.HintStage
import com.ivandsky.puzzlebox.games.strands.StrandsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The path a replay frame draws selected: only a step that added one non-theme word has one, and it spells that word. */
class StrandsReplayNonThemePathTest {

    private val start = StrandsState(StrandsUiFixtures.puzzle)

    @Test
    fun `a non-theme word draws a path that spells it`() {
        val after = start.copy(nonTheme = setOf("PIKE"))
        val path = checkNotNull(nonThemePathOf(start, after))
        assertEquals("PIKE", path.map(after.puzzle::letterAt).joinToString(""))
    }

    @Test
    fun `the first frame, a found word and a hint draw no path`() {
        assertNull(nonThemePathOf(null, start.copy(nonTheme = setOf("PIKE"))))
        assertNull(nonThemePathOf(start, start.copy(found = listOf("PIES"))))
        assertNull(nonThemePathOf(start, start.copy(activeHint = ActiveHint("PIES", HintStage.Outlined))))
    }
}
