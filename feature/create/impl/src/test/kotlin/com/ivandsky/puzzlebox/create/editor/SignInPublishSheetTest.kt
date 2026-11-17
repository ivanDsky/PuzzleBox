package com.ivandsky.puzzlebox.create.editor

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.SIGN_IN_WITH_GOOGLE
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Studio sign-in sheet's button shows the app's one sign-in label; the publish verb stays on the Publish button. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SignInPublishSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the button reads the shared label and signs in`() {
        var taps = 0
        composeRule.setContent {
            PuzzleBoxTheme { SignInPublishSheet(signIn = PublishSession.SignIn(), onGoogle = { taps++ }, onCancel = {}) }
        }

        composeRule.onNodeWithTag("create.signIn.google").assertTextEquals(SIGN_IN_WITH_GOOGLE).performClick()
        assertEquals(1, taps)
    }
}
