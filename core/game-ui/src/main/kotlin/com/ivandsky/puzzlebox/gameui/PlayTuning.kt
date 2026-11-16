package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The fixed play control layout (nav-rethink B5) with its board size caps (B6). A value means the fixed
 * layout; there is no on/off field, the legacy layout is a null [PlayTuning] at [PlayTuningSource].
 *
 * @property tabletLandscapeCap null: the board fills its pane.
 */
data class PlayTuning(
    val phoneCap: Dp,
    val tabletPortraitCap: Dp,
    val tabletLandscapeCap: Dp?,
    val landscapeControls: LandscapeControls,
) {
    /** The largest board side for [deviceClass]; null means no cap. */
    fun boardCapFor(deviceClass: DeviceClass): Dp? = when (deviceClass) {
        DeviceClass.Phone -> phoneCap
        DeviceClass.TabletPortrait -> tabletPortraitCap
        DeviceClass.TabletLandscape -> tabletLandscapeCap
    }

    companion object {
        /** The starting values; the shipped ones are picked by nav-rethink ticket 57. */
        val Starting = PlayTuning(
            phoneCap = 552.dp,
            tabletPortraitCap = 552.dp,
            tabletLandscapeCap = null,
            landscapeControls = LandscapeControls.UnderBoard,
        )
    }
}
