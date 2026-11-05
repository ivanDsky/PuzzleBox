package com.ivandsky.puzzlebox.app.device

import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ivandsky.puzzlebox.app.device.DeviceShell.Companion.GAME
import com.ivandsky.puzzlebox.app.device.DeviceShell.Companion.HISTORY
import com.ivandsky.puzzlebox.app.device.DeviceShell.Companion.HUB
import com.ivandsky.puzzlebox.app.device.DeviceShell.Companion.ZIP
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SC16 (device half): a real orientation change recreates the activity mid-game while two
 * tabs hold non-root stacks (Home → History, Play → Zip). The tab, both stacks and the board
 * survive; the board stays immersive (N2) in both shapes, and outside play the window's new
 * width class swaps the bottom bar for the rail.
 */
@RunWith(AndroidJUnit4::class)
class RotationDeviceTest {

    @get:Rule
    val shell = DeviceShell()

    @Test
    fun rotationMidGameKeepsTabsStacksAndBoardAndSwapsBarAndRail() = with(shell) {
        rotate(SCREEN_ORIENTATION_PORTRAIT)
        tapText(SEE_ALL)
        assertRoute(HISTORY)
        assertBottomBar()
        tapTab(PlayKey)
        openGame(ZIP)
        val left = playMidGame()
        assertNoNavigation()

        rotate(SCREEN_ORIENTATION_LANDSCAPE)

        assertRoute(GAME)
        assertNoNavigation()
        assertEquals("the board survives rotation", left, zipPath())

        rotate(SCREEN_ORIENTATION_PORTRAIT)

        assertRoute(GAME)
        assertNoNavigation()
        assertEquals("the board survives the second rotation", left, zipPath())
        back()
        assertRoute(HUB)
        assertSelectedTab(PlayKey)
        assertBottomBar()
        tapTab(HomeKey)
        assertRoute(HISTORY)

        rotate(SCREEN_ORIENTATION_LANDSCAPE)

        assertRoute(HISTORY)
        assertSelectedTab(HomeKey)
        assertRail()
    }

    private companion object {
        const val SEE_ALL = "See all"
    }
}
