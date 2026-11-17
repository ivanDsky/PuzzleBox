package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * On the narrowest phone the save state lays out whole, one line, even beside the widest verdict
 * pill. Beside that pill the STUDIO tag, then the label and its separator, drop whole.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
class StudioTopBarSaveStateTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `saving beside the widest pill at 360 dp lays out whole`() = assertWhole(saving = true)

    @Test
    fun `saved beside the widest pill at 360 dp lays out whole`() = assertWhole(saving = false)

    @Test
    fun `beside the widest pill the tag, then the label, drop whole and the save state reads alone`() {
        show(saving = true, pill = WIDEST_PILL)
        assertFalse("STUDIO tag was placed beside the pill", layoutOf(TAG).isPlaced)
        assertFalse("subtitle label was placed beside the pill", layoutOf(LABEL).isPlaced)
        assertFalse("separator was placed beside the pill", layoutOf(SEPARATOR).isPlaced)
        status("create.studio.saving").assertTextEquals("Saving…")
    }

    private fun layoutOf(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .also { assertEquals("one node for $tag", 1, it.size) }
        .single()
        .layoutInfo

    private fun assertWhole(saving: Boolean) {
        show(saving, WIDEST_PILL)
        val layouts = mutableListOf<TextLayoutResult>()
        status(if (saving) "create.studio.saving" else "create.studio.saved")
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action!!
            .invoke(layouts)
        val layout = layouts.single()
        assertEquals(1, layout.lineCount)
        assertTrue(layout.size.width >= layout.multiParagraph.maxIntrinsicWidth)
    }

    private fun show(saving: Boolean, pill: @Composable () -> Unit) {
        composeRule.setContent {
            PuzzleBoxTheme {
                StudioTopBar(
                    title = "Castle",
                    subtitle = "Queens 12×12 · Unique · needs guessing",
                    saving = saving,
                    onBack = {},
                    onRename = {},
                    onDuplicate = {},
                    onDelete = {},
                    pill = pill,
                    modifier = Modifier.width(360.dp),
                )
            }
        }
    }

    /** The indicator sits inside the title button, which merges its children's semantics. */
    private fun status(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    private companion object {
        const val TAG = "create.studio.tag"
        const val LABEL = "create.studio.label"
        const val SEPARATOR = "create.studio.separator"
        val WIDEST_PILL: @Composable () -> Unit = {
            VerdictPill(verdict = Verdict.Unique(Solution(listOf(1, 7, 8, 14)), needsGuessing = true), onClick = {})
        }
    }
}
