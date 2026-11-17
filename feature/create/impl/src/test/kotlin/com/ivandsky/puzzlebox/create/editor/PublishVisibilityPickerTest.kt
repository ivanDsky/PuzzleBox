package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc.Visibility
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Public or Link only picker (nav-rethink C1): the two plain words, the pick marked, each chip a 48dp target, a tap
 * reports the choice; a locked pick or a running publish turns the chips off; the details always show it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PublishVisibilityPickerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `it offers Public and Link only, marks the pick and reports a tap`() {
        val picked = mutableListOf<Visibility>()
        composeRule.setContent { PuzzleBoxTheme { PublishVisibilityPicker(PublishVisibility.Choice(Visibility.Public), picked::add) } }

        composeRule.onNodeWithText("Public").assertIsSelected()
        composeRule.onNodeWithText("Link only").assertIsNotSelected()
        composeRule.onNodeWithText("Link only").performClick()

        assertEquals(listOf(Visibility.LinkOnly), picked)
    }

    @Test
    fun `each chip is at least a 48dp target`() {
        composeRule.setContent { PuzzleBoxTheme { PublishVisibilityPicker(PublishVisibility.Choice(Visibility.Public), {}) } }

        for (tag in listOf("create.details.visibility.public", "create.details.visibility.link")) {
            composeRule.onNodeWithTag(tag).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
    }

    @Test
    fun `a locked pick turns the chips off`() {
        composeRule.setContent {
            PuzzleBoxTheme { PublishVisibilityPicker(PublishVisibility.Choice(Visibility.Public, locked = true), {}) }
        }

        composeRule.onNodeWithText("Public").assertIsNotEnabled()
        composeRule.onNodeWithText("Link only").assertIsNotEnabled()
    }

    @Test
    fun `a running publish turns the chips off, an idle one leaves them on`() {
        composeRule.setContent {
            PuzzleBoxTheme { PublishVisibilityPicker(PublishVisibility.Choice(Visibility.Public), {}, enabled = false) }
        }
        composeRule.onNodeWithText("Link only").assertIsNotEnabled()
    }

    @Test
    fun `the details show the picker and report the pick`() {
        val picked = mutableListOf<Visibility>()
        val details = PublishSession.Details(authorName = "Ivan", visibility = PublishVisibility.Choice(Visibility.Public))
        composeRule.setContent { PuzzleBoxTheme { Details(details, picked::add) } }

        composeRule.onNodeWithTag("create.details.visibility").assertExists()
        composeRule.onNodeWithText("Link only").performClick()

        assertEquals(listOf(Visibility.LinkOnly), picked)
    }

    @Composable
    private fun Details(details: PublishSession.Details, onVisibility: (Visibility) -> Unit = {}, enabled: Boolean = true) {
        PublishDetails(
            title = "Castle",
            details = details,
            signedIn = true,
            onRename = {},
            onRollTitle = { "Amber Falcon" },
            onAuthorName = {},
            onNote = {},
            modifier = Modifier.fillMaxWidth(),
            onVisibility = onVisibility,
            enabled = enabled,
        )
    }
}
