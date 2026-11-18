package com.ivandsky.puzzlebox.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class SignInLabelTest {
    // Every sign-in entry point reads this constant; its entry-point tests assert the constant, this one pins the words.
    @Test
    fun `the one sign-in label names the provider`() {
        assertEquals("Sign in with Google", SIGN_IN_WITH_GOOGLE)
    }
}
