package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.navigation.FirstScreenReady
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashKeepOnScreenTest {

    private var now = 0L
    private val firstScreen = FirstScreenReady()
    private val policy = SplashReleasePolicy(firstScreen, nowMillis = { now })

    @Test
    fun `the system splash is kept only while the release policy keeps it`() {
        assertTrue(splashKeepsOnScreen(policy))

        firstScreen.markReady()

        assertFalse(splashKeepsOnScreen(policy))
    }

    @Test
    fun `the one-second cap releases the system splash through the same check`() {
        now += SplashReleasePolicy.MAX_HOLD_MILLIS

        assertFalse(splashKeepsOnScreen(policy))
    }

    @Test
    fun `a failed local read releases the system splash through the same check`() {
        policy.onLocalReadFailed()

        assertFalse(splashKeepsOnScreen(policy))
    }
}
