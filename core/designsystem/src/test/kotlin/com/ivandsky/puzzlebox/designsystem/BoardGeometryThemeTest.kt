package com.ivandsky.puzzlebox.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The board-geometry fact (nav-rethink B3): legacy by default so previews and tests keep today's
// board; the theme's one Boolean picks the geometry and follows a live change.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BoardGeometryThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `legacy geometry is today's board`() {
        assertEquals(BoardTokens.boardWallWeight, BoardGeometry.Legacy.wallWeight)
        assertFalse(BoardGeometry.Legacy.roundedWallCaps)
        assertFalse(BoardGeometry.Legacy.nextNumberRing)
        assertFalse(BoardGeometry.Legacy.wallOutline)
    }

    @Test
    fun `readable geometry has a lighter wall with round caps and the next-number ring`() {
        assertTrue(BoardGeometry.Readable.wallWeight > BoardTokens.boardGridLineWeight)
        assertTrue(BoardGeometry.Readable.wallWeight < BoardGeometry.Legacy.wallWeight)
        assertTrue(BoardGeometry.Readable.roundedWallCaps)
        assertTrue(BoardGeometry.Readable.nextNumberRing)
        assertTrue(BoardGeometry.Readable.wallOutline)
    }

    @Test
    fun `a theme with no request provides legacy geometry`() {
        var seen: BoardGeometry? = null
        composeRule.setContent { PuzzleBoxTheme { seen = LocalBoardGeometry.current } }
        composeRule.waitForIdle()
        assertEquals(BoardGeometry.Legacy, seen)
    }

    @Test
    fun `the theme provides the geometry its request picks and follows a change`() {
        var readable by mutableStateOf(true)
        var seen: BoardGeometry? = null
        composeRule.setContent { PuzzleBoxTheme(readableBoard = readable) { seen = LocalBoardGeometry.current } }
        composeRule.waitForIdle()
        assertEquals(BoardGeometry.Readable, seen)

        composeRule.runOnUiThread { readable = false }
        composeRule.waitForIdle()
        assertEquals(BoardGeometry.Legacy, seen)
    }
}
