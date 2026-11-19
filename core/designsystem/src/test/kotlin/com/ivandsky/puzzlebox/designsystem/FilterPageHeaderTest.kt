package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The header of an option page (epic picker-filters, ticket 02): a Back arrow and a heading, in a row of at least 56dp. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class FilterPageHeaderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var backs = 0

    private fun show() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                FilterPageHeader(title = "Games", onBack = { backs++ }, modifier = Modifier.testTag("header"), backTag = "x.page.back")
            }
        }
    }

    @Test
    fun `the back arrow is 48dp, described Back, tagged, and goes back once`() {
        show()

        composeRule.onNodeWithTag("x.page.back").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, backs)
    }

    @Test
    fun `the title is a heading and the row is at least 56dp tall`() {
        show()

        composeRule.onNodeWithText("Games").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule.onNodeWithTag("header").assertHeightIsAtLeast(56.dp)
    }
}
