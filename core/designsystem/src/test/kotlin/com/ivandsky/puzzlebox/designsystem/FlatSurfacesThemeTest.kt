package com.ivandsky.puzzlebox.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The flat-surfaces fact (nav-rethink B1): off by default so previews and tests keep today's
// look; the theme's argument reaches every component below it and follows a live change.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FlatSurfacesThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a theme with no request is not flat`() {
        var seen: Boolean? = null
        composeRule.setContent { PuzzleBoxTheme { seen = LocalFlatSurfaces.current } }
        composeRule.waitForIdle()
        assertEquals(false, seen)
    }

    @Test
    fun `the theme hands the flat request to the content and follows a change`() {
        var flat by mutableStateOf(true)
        var seen: Boolean? = null
        composeRule.setContent { PuzzleBoxTheme(flatSurfaces = flat) { seen = LocalFlatSurfaces.current } }
        composeRule.waitForIdle()
        assertEquals(true, seen)

        composeRule.runOnUiThread { flat = false }
        composeRule.waitForIdle()
        assertEquals(false, seen)
    }

    @Test
    fun `flat strands is its own request, off by default and independent of flat surfaces`() {
        var surfacesOn by mutableStateOf(false)
        var strands: Boolean? = null
        var surfaces: Boolean? = null
        composeRule.setContent {
            PuzzleBoxTheme(flatSurfaces = surfacesOn) { strands = LocalFlatStrands.current; surfaces = LocalFlatSurfaces.current }
        }
        composeRule.waitForIdle()
        assertEquals(false, strands)

        composeRule.runOnUiThread { surfacesOn = true }
        composeRule.waitForIdle()
        assertEquals(false, strands)
        assertEquals(true, surfaces)
    }
}
