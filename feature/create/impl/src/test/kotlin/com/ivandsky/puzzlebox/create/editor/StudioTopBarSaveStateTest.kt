package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** On the narrowest phone the save state is never squeezed below its own words: it lays out whole, one line. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
class StudioTopBarSaveStateTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `saving at 360 dp lays out whole`() = assertWhole(saving = true)

    @Test
    fun `saved at 360 dp lays out whole`() = assertWhole(saving = false)

    private fun assertWhole(saving: Boolean) {
        composeRule.setContent {
            PuzzleBoxTheme {
                StudioTopBar(
                    title = "Castle",
                    subtitle = "Queens 7×7 · Unique",
                    saving = saving,
                    onBack = {},
                    onRename = {},
                    onDuplicate = {},
                    onDelete = {},
                    pill = {},
                    modifier = Modifier.width(360.dp),
                )
            }
        }
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

    /** The indicator sits inside the title button, which merges its children's semantics. */
    private fun status(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag, useUnmergedTree = true)
}
