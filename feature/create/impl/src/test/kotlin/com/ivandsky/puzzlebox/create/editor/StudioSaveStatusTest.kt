package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Studio bar's autosave indicator (02 handoff "Autosave"): "Saving…" while an edit waits, then "✓ Saved". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StudioSaveStatusTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the indicator reads saving while an edit waits, then saved`() {
        var saving by mutableStateOf(true)
        composeRule.setContent {
            PuzzleBoxTheme {
                StudioTopBar(
                    title = "Castle",
                    subtitle = "Queens 7×7",
                    saving = saving,
                    onBack = {},
                    onRename = {},
                    onDuplicate = {},
                    onDelete = {},
                    pill = {},
                )
            }
        }
        status("create.studio.saving").assertIsDisplayed()
        status("create.studio.saved").assertDoesNotExist()

        saving = false
        status("create.studio.saved").assertIsDisplayed()
        status("create.studio.saving").assertDoesNotExist()
    }

    /** The indicator sits inside the title button, which merges its children's semantics. */
    private fun status(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true)
}
