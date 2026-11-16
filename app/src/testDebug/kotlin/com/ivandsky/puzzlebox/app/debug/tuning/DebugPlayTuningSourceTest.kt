package com.ivandsky.puzzlebox.app.debug.tuning

import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.gameui.DeviceClass
import com.ivandsky.puzzlebox.gameui.LandscapeControls
import com.ivandsky.puzzlebox.gameui.PlayTuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class DebugPlayTuningSourceTest {

    private val context = RuntimeEnvironment.getApplication()

    private fun source() = DebugPlayTuningSource(context)

    @Test
    fun `a fresh source holds the starting values`() {
        assertEquals(PlayTuning.Starting, source().tuning.value)
    }

    @Test
    fun `the phone cap changes the phone class only`() {
        val source = source()

        source.setCap(DeviceClass.Phone, 400.dp)

        assertEquals(PlayTuning.Starting.copy(phoneCap = 400.dp), source.tuning.value)
    }

    @Test
    fun `the tablet portrait cap changes the tablet portrait class only`() {
        val source = source()

        source.setCap(DeviceClass.TabletPortrait, 720.dp)

        assertEquals(PlayTuning.Starting.copy(tabletPortraitCap = 720.dp), source.tuning.value)
    }

    @Test
    fun `the tablet landscape cap changes the tablet landscape class only`() {
        val source = source()

        source.setCap(DeviceClass.TabletLandscape, 640.dp)

        assertEquals(PlayTuning.Starting.copy(tabletLandscapeCap = 640.dp), source.tuning.value)
    }

    @Test
    fun `the landscape controls switch changes only the placement`() {
        val source = source()

        source.setLandscapeControls(LandscapeControls.SideColumn)

        assertEquals(PlayTuning.Starting.copy(landscapeControls = LandscapeControls.SideColumn), source.tuning.value)
    }

    @Test
    fun `fill the pane is a null tablet landscape cap and a cap takes it back`() {
        val source = source()
        source.setCap(DeviceClass.TabletLandscape, 640.dp)

        source.fillTabletLandscapePane()
        assertNull(source.tuning.value.tabletLandscapeCap)
        assertEquals(PlayTuning.Starting.phoneCap, source.tuning.value.phoneCap)

        source.setCap(DeviceClass.TabletLandscape, 600.dp)
        assertEquals(600.dp, source.tuning.value.tabletLandscapeCap)
    }

    @Test
    fun `every value survives a new source over the same preferences`() {
        source().apply {
            setCap(DeviceClass.Phone, 400.dp)
            setCap(DeviceClass.TabletPortrait, 720.dp)
            setCap(DeviceClass.TabletLandscape, 640.dp)
            setLandscapeControls(LandscapeControls.SideColumn)
        }

        assertEquals(
            PlayTuning(400.dp, 720.dp, 640.dp, LandscapeControls.SideColumn),
            source().tuning.value,
        )
    }

    @Test
    fun `fill the pane survives a new source over the same preferences`() {
        source().apply {
            setCap(DeviceClass.TabletLandscape, 640.dp)
            fillTabletLandscapePane()
        }

        assertNull(source().tuning.value.tabletLandscapeCap)
    }

    @Test
    fun `caps outside 240 to 960 dp are clamped`() {
        val source = source()

        source.setCap(DeviceClass.Phone, 100.dp)
        source.setCap(DeviceClass.TabletPortrait, 2000.dp)
        source.setCap(DeviceClass.TabletLandscape, -8.dp)

        assertEquals(PlayTuning(240.dp, 960.dp, 240.dp, LandscapeControls.UnderBoard), source.tuning.value)
        assertEquals(PlayTuning(240.dp, 960.dp, 240.dp, LandscapeControls.UnderBoard), source().tuning.value)
    }

    @Test
    fun `a stored cap outside the range is clamped on read`() {
        context.getSharedPreferences("play_tuning", android.content.Context.MODE_PRIVATE)
            .edit().putInt("phone_cap_dp", 5000).putInt("tablet_portrait_cap_dp", 1).commit()

        assertEquals(PlayTuning.Starting.copy(phoneCap = 960.dp, tabletPortraitCap = 240.dp), source().tuning.value)
    }

    @Test
    fun `reset puts every value back to starting, in memory and in the preferences`() {
        val source = source()
        source.setCap(DeviceClass.Phone, 400.dp)
        source.setCap(DeviceClass.TabletLandscape, 640.dp)
        source.setLandscapeControls(LandscapeControls.SideColumn)

        source.reset()

        assertEquals(PlayTuning.Starting, source.tuning.value)
        assertEquals(PlayTuning.Starting, source().tuning.value)
    }
}
