package com.ivandsky.puzzlebox.home

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Golden of the Today doors with the callbacks set (phone, 360 dp floor, light): the streak
 * row's chevron, the tappable week and the Recent rows' chevrons. Goldens live in
 * `feature/home/impl/snapshots/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class HomeDoorsGoldenTest : HomeStreakScenes() {
    @Test
    fun `doors on the phone light`() {
        render(dark = false) { HomeDoorsPhonePreview() }
        composeRule.onNodeWithTag("home.streak_door").assertExists()
        val out = File(checkNotNull(System.getProperty("user.dir"))).resolve("snapshots/home-doors-phone360-light.png")
        composeRule.onRoot().captureRoboImage(out.canonicalFile.absolutePath)
    }
}
