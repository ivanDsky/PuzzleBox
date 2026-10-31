package com.ivandsky.puzzlebox.home

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutPolicyTest {

    @Test
    fun `expanded width earns the two-pane list-detail layout`() {
        assertEquals(HomeLayout.TwoPane, homeLayoutFor(WindowWidthSizeClass.Expanded))
    }

    @Test
    fun `medium width stays single-pane`() {
        assertEquals(HomeLayout.SinglePane, homeLayoutFor(WindowWidthSizeClass.Medium))
    }

    @Test
    fun `compact width stays single-pane`() {
        assertEquals(HomeLayout.SinglePane, homeLayoutFor(WindowWidthSizeClass.Compact))
    }
}
