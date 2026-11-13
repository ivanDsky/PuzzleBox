package com.ivandsky.puzzlebox.profile.linkedin

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Robolectric compose coverage for the LinkedIn risk warning: its two buttons and the phrases it must keep. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class LinkedInRiskWarningTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var accepts = 0
    private var cancels = 0

    private fun setContent() {
        composeRule.setContent {
            PuzzleBoxTheme {
                LinkedInRiskWarning(onAccept = { accepts++ }, onCancel = { cancels++ })
            }
        }
    }

    @Test
    fun `tapping accept reports accept once and cancel never`() {
        setContent()

        composeRule.onNodeWithTag("linkedin.warning.accept").performScrollTo().performClick()

        assertEquals(1, accepts)
        assertEquals(0, cancels)
    }

    @Test
    fun `tapping cancel reports cancel once and accept never`() {
        setContent()

        composeRule.onNodeWithTag("linkedin.warning.cancel").performScrollTo().performClick()

        assertEquals(1, cancels)
        assertEquals(0, accepts)
    }

    @Test
    fun `the warning names the user agreement breach and each of the four facts`() {
        setContent()

        composeRule.onNodeWithTag("linkedin.warning").assertExists()
        listOf(
            "§8.2",
            "only reads",
            "never uploaded",
            "never sees it",
            "does not sign you out",
        ).forEach { phrase ->
            composeRule.onNodeWithText(phrase, substring = true).performScrollTo().assertExists()
        }
    }
}
