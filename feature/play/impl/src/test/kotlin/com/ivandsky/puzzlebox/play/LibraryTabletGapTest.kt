package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The tablet's Games list under the Dailies/Duels strip: the first card sits the same gap below the strip as
 * the gap between two cards, also after scrolling back to the top, and the selected outline stays inside the
 * list's side edges.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h500dp")
class LibraryTabletGapTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val scroll = ScrollState(0)

    private fun show(tab: GamesTab) {
        composeRule.setContent {
            CompositionLocalProvider(LocalTabReselect provides MutableSharedFlow()) {
                PuzzleBoxTheme {
                    val duels = remember { gapDuels() }
                    LibraryList(
                        state = previewState(),
                        layout = PlayLayout.TwoPane,
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

        assertGapMatches()
        runBlocking { scroll.scrollTo(scroll.maxValue) }
        composeRule.waitForIdle()
        runBlocking { scroll.scrollTo(0) }
        composeRule.waitForIdle()
        assertGapMatches()
    }

    private fun assertGapMatches() {
        val strip = bounds("underline.tabs")
        val first = bounds("library.row.zip")
        val second = bounds("library.row.tango")
        val between = second.top - first.bottom
        assertEquals("gap under the strip", between, first.top - strip.bottom, 0.5f)
    }

    @Test
    fun `the selected outline stays inside the list's side edges`() {
        show(GamesTab.Dailies)

        val list = bounds("library.list")
        val zip = bounds("library.row.zip")
        val room = dp(OutlineRoom.value)
        assertTrue("outline left ${zip.left - room} is left of the list's ${list.left}", zip.left - room >= list.left - 0.5f)
        assertTrue("outline right ${zip.right + room} is right of the list's ${list.right}", zip.right + room <= list.right + 0.5f)
    }

    @Test
    fun `the first Duels row sits a row gap below the strip`() {
        show(GamesTab.Duels)

        val strip = bounds("underline.tabs")
        assertEquals(dp(Spacing.lg.value), bounds("duel.first").top - strip.bottom, 0.5f)
    }
}

/** Duels' real list pads its top by [Spacing.sm] and spaces rows [Spacing.lg]; the stand-in keeps both. */
private fun gapDuels() = DuelsSlot(
    content = { state, _, _ ->
        LazyColumn(
            Modifier.fillMaxWidth(),
            state = state,
            contentPadding = PaddingValues(vertical = Spacing.sm),
        ) {
            items(3) { Text("duel $it", Modifier.height(48.dp).testTag(if (it == 0) "duel.first" else "duel.$it")) }
        }
    },
    needYou = { 0 },
    onOpenDuel = {},
    pane = { _ -> },
)
