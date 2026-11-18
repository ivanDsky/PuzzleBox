package com.ivandsky.puzzlebox.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The board-geometry fact (nav-rethink B3): one value, the theme provides it and so does the bare default.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BoardGeometryThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the wall is heavier than the grid line and lighter than the stroke default`() {
        assertTrue(BoardGeometry.Readable.wallWeight > BoardTokens.boardGridLineWeight)
        assertTrue(BoardGeometry.Readable.wallWeight < BoardTokens.boardWallWeight)
    }

    @Test
    fun `the theme provides the geometry, and so does a bare composition`() {
        var themed: BoardGeometry? = null
        var bare: BoardGeometry? = null
        composeRule.setContent {
            bare = LocalBoardGeometry.current
            PuzzleBoxTheme { themed = LocalBoardGeometry.current }
        }
        composeRule.waitForIdle()
        assertEquals(BoardGeometry.Readable, themed)
        assertEquals(BoardGeometry.Readable, bare)
    }
}
