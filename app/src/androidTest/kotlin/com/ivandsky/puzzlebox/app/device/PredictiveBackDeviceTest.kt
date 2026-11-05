package com.ivandsky.puzzlebox.app.device

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ivandsky.puzzlebox.app.device.DeviceShell.Companion.GAME
import com.ivandsky.puzzlebox.app.device.DeviceShell.Companion.HUB
import com.ivandsky.puzzlebox.app.device.DeviceShell.Companion.ZIP
import com.ivandsky.puzzlebox.play.api.PlayKey
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SC16 (device half): a predictive back gesture on a live board, driven through the real
 * activity's back dispatcher while NavDisplay plays its seekable transition on device.
 */
@RunWith(AndroidJUnit4::class)
class PredictiveBackDeviceTest {

    @get:Rule
    val shell = DeviceShell()

    @Test
    fun predictiveBackCommitReturnsToTheHubAndKeepsTheSession() = with(shell) {
        tapTab(PlayKey)
        openGame(ZIP)
        val left = playMidGame()

        predictiveBack(commit = true)

        assertRoute(HUB)
        assertSelectedTab(PlayKey)
        openGame(ZIP)
        assertEquals("the board survives the committed gesture", left, zipPath())
    }

    @Test
    fun predictiveBackCancelStaysOnTheBoard() = with(shell) {
        tapTab(PlayKey)
        openGame(ZIP)
        val left = playMidGame()

        predictiveBack(commit = false)

        assertRoute(GAME)
        assertSelectedTab(PlayKey)
        assertEquals("a cancelled gesture leaves the board as it was", left, zipPath())
    }
}
