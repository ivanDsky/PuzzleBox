package com.ivandsky.puzzlebox.home

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutPolicyTest {

    @Test
    fun `expanded width in landscape earns the two-pane list-detail layout`() {
        assertEquals(
            HomeLayout.TwoPane,
            homeLayoutFor(WindowWidthSizeClass.Expanded, isLandscape = true),
        )
    }

    @Test
    fun `expanded width in portrait stays single-pane`() {
        assertEquals(
            HomeLayout.SinglePane,
            homeLayoutFor(WindowWidthSizeClass.Expanded, isLandscape = false),
        )
    }

    @Test
    fun `medium width stays single-pane in either orientation`() {
        assertEquals(
            HomeLayout.SinglePane,
            homeLayoutFor(WindowWidthSizeClass.Medium, isLandscape = true),
        )
        assertEquals(
            HomeLayout.SinglePane,
            homeLayoutFor(WindowWidthSizeClass.Medium, isLandscape = false),
        )
    }

    @Test
    fun `compact width stays single-pane in either orientation`() {
        assertEquals(
            HomeLayout.SinglePane,
            homeLayoutFor(WindowWidthSizeClass.Compact, isLandscape = true),
        )
        assertEquals(
            HomeLayout.SinglePane,
            homeLayoutFor(WindowWidthSizeClass.Compact, isLandscape = false),
        )
    }
}
