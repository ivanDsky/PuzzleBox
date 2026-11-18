package com.ivandsky.puzzlebox.compete.create

import androidx.compose.ui.test.assertIsNotEnabled
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

/** The Duels sheet's sign-in step shows the app's one sign-in label, not its own wording. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DuelSignInPanelTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var taps = 0

    private fun show(signIn: DuelSignIn) {
        val actions = NewDuelActions({}, {}, {}, {}, {}, {}, {}, { taps++ }, {})
        composeRule.setContent { PuzzleBoxTheme { SignInPanel(friend().copy(signIn = signIn), actions) } }
    }

    @Test
    fun `the button reads the shared label and signs in`() {
        show(DuelSignIn.Idle)

        composeRule.onNodeWithTag("duel.signIn.google").assertTextEquals(SIGN_IN_WITH_GOOGLE).performClick()
        assertEquals(1, taps)
    }

    @Test
    fun `while signing in the button says so and is off`() {
        show(DuelSignIn.SigningIn)

        composeRule.onNodeWithTag("duel.signIn.google").assertTextEquals("Signing in…").assertIsNotEnabled()
    }
}
