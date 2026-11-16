package com.ivandsky.puzzlebox.app.debug.tuning

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.DeviceClass
import com.ivandsky.puzzlebox.gameui.LandscapeControls
import com.ivandsky.puzzlebox.gameui.PlayTuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Tall enough for every row, Reset included: the panel does not scroll by itself.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w360dp-h1000dp")
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
            PlayTuning.Shipped.copy(phoneCap = 560.dp, tabletPortraitCap = 632.dp),
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
        composeRule.onNodeWithTag("tuning.tabletLandscape.value").assertTextEquals("640 dp")

        composeRule.onNodeWithTag("tuning.fill").performClick()
        composeRule.onNodeWithTag("tuning.tabletLandscape").assertDoesNotExist()
        assertNull(source.tuning.value.tabletLandscapeCap)

        composeRule.onNodeWithTag("tuning.fill").performClick()
        composeRule.onNodeWithTag("tuning.tabletLandscape.value").assertTextEquals("552 dp")
        assertEquals(552.dp, source.tuning.value.tabletLandscapeCap)
    }

    @Test
    fun `the tablet cell cap stepper is gone while the cap is off and its switch turns it back on at 72 dp`() {
        show()
        composeRule.onNodeWithTag("tuning.cell.value").assertTextEquals("80 dp")

        composeRule.onNodeWithTag("tuning.cellCap").performClick()
        assertNull(source.tuning.value.tabletCellCap)
        composeRule.onNodeWithTag("tuning.cell").assertDoesNotExist()

        composeRule.onNodeWithTag("tuning.cellCap").performClick()
        composeRule.onNodeWithTag("tuning.cell.value").assertTextEquals("72 dp")
        assertEquals(72.dp, source.tuning.value.tabletCellCap)
    }

    @Test
    fun `the tablet cell cap stepper moves by 8 dp and stops at 40 and 120`() {
        source.setTabletCellCap(72.dp)
        show()

        composeRule.onNodeWithTag("tuning.cell.more").performClick()
        composeRule.onNodeWithTag("tuning.cell.value").assertTextEquals("80 dp")
        assertEquals(80.dp, source.tuning.value.tabletCellCap)

        composeRule.onNodeWithTag("tuning.cell.less").performClick().performClick()
        assertEquals(64.dp, source.tuning.value.tabletCellCap)

        source.setTabletCellCap(40.dp)
        composeRule.onNodeWithTag("tuning.cell.less").assertIsNotEnabled()
        source.setTabletCellCap(120.dp)
        composeRule.onNodeWithTag("tuning.cell.more").assertIsNotEnabled()
        composeRule.onNodeWithTag("tuning.cell.less").assertIsEnabled()
    }

    @Test
    fun `the tablet cell cap stepper buttons are 48 dp touch targets`() {
        source.setTabletCellCap(72.dp)
        show()

        listOf("tuning.cell.less", "tuning.cell.more").forEach { tag ->
            val bounds = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue("$tag $bounds", bounds.width >= 48.dp && bounds.height >= 48.dp)
        }
    }

    @Test
    fun `the side column switch sets the landscape controls`() {
        show()

        composeRule.onNodeWithTag("tuning.sideColumn").performClick()

        assertEquals(LandscapeControls.UnderBoard, source.tuning.value.landscapeControls)
    }

    @Test
    fun `reset puts every value back to the shipped ones`() {
        source.setCap(DeviceClass.Phone, 400.dp)
        source.setCap(DeviceClass.TabletLandscape, 640.dp)
        source.setLandscapeControls(LandscapeControls.UnderBoard)
        source.setTabletCellCap(null)
        show()

        composeRule.onNodeWithTag("tuning.reset").performClick()

        assertEquals(PlayTuning.Shipped, source.tuning.value)
    }
}
