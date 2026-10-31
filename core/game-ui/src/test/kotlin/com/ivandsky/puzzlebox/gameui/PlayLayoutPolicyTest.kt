package com.ivandsky.puzzlebox.gameui

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import org.junit.Assert.assertEquals
import org.junit.Test

/** Same policy contract home's layout test pins (A12 / ticket 07), restated for play. */
class PlayLayoutPolicyTest {

    @Test
    fun `expanded width in landscape is two pane`() {
        assertEquals(
            PlayLayout.TwoPane,
            playLayoutFor(WindowWidthSizeClass.Expanded, isLandscape = true),
        )
    }

    @Test
    fun `expanded width in portrait stays single pane`() {
        assertEquals(
            PlayLayout.SinglePane,
            playLayoutFor(WindowWidthSizeClass.Expanded, isLandscape = false),
        )
    }

    @Test
    fun `compact and medium are always single pane`() {
        assertEquals(PlayLayout.SinglePane, playLayoutFor(WindowWidthSizeClass.Compact, isLandscape = true))
        assertEquals(PlayLayout.SinglePane, playLayoutFor(WindowWidthSizeClass.Medium, isLandscape = true))
    }
}
