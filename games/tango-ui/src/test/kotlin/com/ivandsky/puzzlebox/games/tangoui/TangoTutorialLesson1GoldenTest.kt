package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tutorial lesson 1's open squares must read as invitations, not disabled content (#1186). The goldens run only under
 * `-Proborazzi.verify` / `-Proborazzi.record=true` ([GoldenCompare]).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-420dpi")
class TangoTutorialLesson1GoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, dark: Boolean) {
        composeRule.setContent { TangoLessonOneSample(dark) }
        composeRule.waitForIdle()
        val scratch = File("build/roborecord/$name").apply { parentFile?.mkdirs() }
        composeRule.onRoot().captureRoboImage(scratch)
        GoldenCompare.check(File("src/test/resources/goldens/$name"), scratch, name)
    }

    @Test fun `golden lesson 1 light`() = capture("tango-tutorial-lesson1-light.png", dark = false)

    @Test fun `golden lesson 1 dark`() = capture("tango-tutorial-lesson1-dark.png", dark = true)
}
