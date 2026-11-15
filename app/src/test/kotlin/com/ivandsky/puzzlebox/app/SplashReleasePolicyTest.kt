package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.navigation.FirstScreenReady
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashReleasePolicyTest {

    private var now = 5_000L
    private val signal = FirstScreenReady()
    private val policy = SplashReleasePolicy(signal, nowMillis = { now })

    @Test
    fun `the splash stays while nothing is ready, failed or late`() {
        assertTrue(policy.keepSplash())
        now += 999
        assertTrue(policy.keepSplash())
    }

    @Test
    fun `Home ready releases it`() {
        signal.markReady()

        assertFalse(policy.keepSplash())
    }

    @Test
    fun `a non-Home landing releases it the same way`() {
        // The shell marks the same signal at first composition; the policy cannot tell the two apart.
        signal.markReady()
        now += 10

        assertFalse(policy.keepSplash())
    }

    @Test
    fun `a local read failure releases it at once`() {
        policy.onLocalReadFailed()

        assertFalse(policy.keepSplash())
    }

    @Test
    fun `one second releases it even when nothing else happened`() {
        now += SplashReleasePolicy.MAX_HOLD_MILLIS

        assertFalse(policy.keepSplash())
    }

    @Test
    fun `the cap counts from the policy's start, not from process time zero`() {
        now += SplashReleasePolicy.MAX_HOLD_MILLIS - 1

        assertTrue(policy.keepSplash())
    }
}
