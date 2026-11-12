package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import org.junit.Assert.assertEquals
import org.junit.Test

/** A18: the compare follows the window width alone. */
class CompareLayoutPolicyTest {

    @Test
    fun `each width class maps to one layout`() {
        val table = listOf(
            WindowWidthSizeClass.Compact to CompareLayout.FlipPeek,
            WindowWidthSizeClass.Medium to CompareLayout.FlipPeek,
            WindowWidthSizeClass.Expanded to CompareLayout.Twin,
        )
        for ((widthSizeClass, expected) in table) {
            assertEquals("$widthSizeClass", expected, compareLayoutFor(widthSizeClass))
        }
    }
}
