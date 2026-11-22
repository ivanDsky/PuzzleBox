package com.ivandsky.puzzlebox.play.community.shelves

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The entry tiles at the 411dp phone, with real text metrics (native graphics) so a wrapping title shows. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class ShelvesEntryTilesTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun lines(title: String): Int {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(title).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single().lineCount
    }

    @Test
    fun `both entry titles stay on one line at 411dp with three senders`() {
        val entries = ShelvesEntries(friendPuzzles = 7, friendInitials = listOf('M', 'B', 'O'), published = 3, drafts = 2)
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.requiredWidth(411.dp)) { ShelvesEntryTiles(entries, ShelvesActions(onEnterCode = {}, onOpen = {}, onRetry = {}), Modifier.padding(horizontal = 16.dp)) }
            }
        }
        assertEquals(1, lines("From friends"))
        assertEquals(1, lines("Your puzzles"))
    }
}
