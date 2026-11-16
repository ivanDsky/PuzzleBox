package com.ivandsky.puzzlebox.app.debug.tuning

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.DeviceClass
import com.ivandsky.puzzlebox.gameui.LandscapeControls
import com.ivandsky.puzzlebox.gameui.PlayTuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class PlayTuningPanelTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val source = DebugPlayTuningSource(RuntimeEnvironment.getApplication())

    private fun show() = composeRule.setContent { PuzzleBoxTheme { PlayTuningPanel(source = source) } }

    @Test
    fun `a stepper moves its device class by 8 dp`() {
        show()

        composeRule.onNodeWithTag("tuning.phone.more").performClick()
        composeRule.onNodeWithTag("tuning.tabletPortrait.less").performClick()

        composeRule.onNodeWithTag("tuning.phone.value").assertTextEquals("560 dp")
        assertEquals(
            PlayTuning.Starting.copy(phoneCap = 560.dp, tabletPortraitCap = 544.dp),
            source.tuning.value,
        )
    }

    @Test
    fun `a stepper stops at the ends of the range`() {
        source.setCap(DeviceClass.Phone, 240.dp)
        source.setCap(DeviceClass.TabletPortrait, 960.dp)
        show()

        composeRule.onNodeWithTag("tuning.phone.less").assertIsNotEnabled()
        composeRule.onNodeWithTag("tuning.phone.more").assertIsEnabled()
        composeRule.onNodeWithTag("tuning.tabletPortrait.more").assertIsNotEnabled()
    }

    @Test
    fun `the tablet landscape stepper is gone while the pane is filled and Fill the pane toggles it`() {
        show()
        composeRule.onNodeWithTag("tuning.tabletLandscape").assertDoesNotExist()

        composeRule.onNodeWithTag("tuning.fill").performClick()
        composeRule.onNodeWithTag("tuning.tabletLandscape.value").assertTextEquals("552 dp")
        assertEquals(552.dp, source.tuning.value.tabletLandscapeCap)

        composeRule.onNodeWithTag("tuning.fill").performClick()
        assertNull(source.tuning.value.tabletLandscapeCap)
    }

    @Test
    fun `the side column switch sets the landscape controls`() {
        show()

        composeRule.onNodeWithTag("tuning.sideColumn").performClick()

        assertEquals(LandscapeControls.SideColumn, source.tuning.value.landscapeControls)
    }

    @Test
    fun `reset puts every value back to starting`() {
        source.setCap(DeviceClass.Phone, 400.dp)
        source.setCap(DeviceClass.TabletLandscape, 640.dp)
        source.setLandscapeControls(LandscapeControls.SideColumn)
        show()

        composeRule.onNodeWithTag("tuning.reset").performClick()

        assertEquals(PlayTuning.Starting, source.tuning.value)
    }
}
