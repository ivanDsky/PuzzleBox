package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalTabReselect
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.PlayLayout
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The phone's Games list under the Dailies/Duels strip: the first card sits a card gap below the strip, the first
 * Duels row the same gap the tablet gives it, also after scrolling back to the top, and the offline pill keeps the
 * card gap above it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class LibraryPhoneGapTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val scroll = ScrollState(0)

    private fun show(tab: GamesTab, state: LibraryUiState = previewState()) {
        composeRule.setContent {
            CompositionLocalProvider(LocalTabReselect provides MutableSharedFlow()) {
                PuzzleBoxTheme {
                    val duels = remember { gapDuels() }
                    LibraryList(
                        state = state,
                        layout = PlayLayout.SinglePane,
                        scroll = scroll,
                        onRow = {},
                        modifier = Modifier.width(400.dp),
                        duels = duels,
                        tab = tab,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun bounds(tag: String): Rect = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun dp(value: Float) = with(composeRule.density) { value.dp.toPx() }

    @Test
    fun `the first Dailies card sits a card gap below the strip, also back at the top`() {
        show(GamesTab.Dailies)

        assertCardGapBelowStrip()
        runBlocking { scroll.scrollTo(scroll.maxValue) }
        composeRule.waitForIdle()
        runBlocking { scroll.scrollTo(0) }
        composeRule.waitForIdle()
        assertCardGapBelowStrip()
    }

    @Test
    fun `the offline pill keeps the card gap above it`() {
        show(GamesTab.Dailies, previewState().copy(offline = true))

        val strip = bounds("underline.tabs")
        val pill = bounds("library.offline")
        val first = bounds("library.row.zip")
        val second = bounds("library.row.tango")
        assertEquals("gap above the offline pill", second.top - first.bottom, pill.top - strip.bottom, 0.5f)
    }

    @Test
    fun `the first Duels row sits the tablet's Duels gap below the strip`() {
        show(GamesTab.Duels)

        val strip = bounds("underline.tabs")
        assertEquals(dp(Spacing.lg.value), bounds("duel.first").top - strip.bottom, 0.5f)
    }

    private fun assertCardGapBelowStrip() {
        val strip = bounds("underline.tabs")
        val first = bounds("library.row.zip")
        val second = bounds("library.row.tango")
        assertEquals("gap under the strip", second.top - first.bottom, first.top - strip.bottom, 0.5f)
    }
}
