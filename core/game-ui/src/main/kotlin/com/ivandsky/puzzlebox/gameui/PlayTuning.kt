package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The fixed play control layout (nav-rethink B5) with its board size caps (B6). A value means the fixed
 * layout; there is no on/off field, the legacy layout is a null [PlayTuning] at [PlayTuningSource].
 *
 * @property tabletLandscapeCap null: the board fills its pane.
 * @property tabletCellCap the widest a grid cell may grow on a tablet class, so a board is at most this times the
 * grid's columns (see [boardCapForGrid]); null: no cell cap. The phone never has one.
 */
data class PlayTuning(
    val phoneCap: Dp,
    val tabletPortraitCap: Dp,
    val tabletLandscapeCap: Dp?,
    val landscapeControls: LandscapeControls,
    val tabletCellCap: Dp? = null,
) {
    /** The largest board side for [deviceClass]; null means no cap. */
    fun boardCapFor(deviceClass: DeviceClass): Dp? = when (deviceClass) {
        DeviceClass.Phone -> phoneCap
        DeviceClass.TabletPortrait -> tabletPortraitCap
        DeviceClass.TabletLandscape -> tabletLandscapeCap
    }

    /**
     * The largest board side for [deviceClass] on a grid of [gridColumns]: the class cap, and on the tablet classes
     * also [tabletCellCap] times the columns, whichever is smaller. Null [gridColumns] or [tabletCellCap]: the class cap.
     */
    fun boardCapForGrid(deviceClass: DeviceClass, gridColumns: Int?): Dp? {
        val classCap = boardCapFor(deviceClass)
        val cellCap = tabletCellCap
        if (deviceClass == DeviceClass.Phone || gridColumns == null || cellCap == null) return classCap
        val gridCap = cellCap * gridColumns
        return if (classCap == null || gridCap < classCap) gridCap else classCap
    }

    companion object {
        /**
         * The values the user chose on the S9 Ultra (nav-rethink ticket 56, widened by ticket 57c): the phone keeps today's
         * 552 dp, tablet boards are at most 640 dp in portrait and in landscape and at most 80 dp per cell, and landscape
         * controls sit in a side column. The shipped source emits it; the debug source starts from it and resets to it.
         */
        val Shipped = PlayTuning(
            phoneCap = 552.dp,
            tabletPortraitCap = 640.dp,
            tabletLandscapeCap = 640.dp,
            landscapeControls = LandscapeControls.SideColumn,
            tabletCellCap = 80.dp,
        )

        /** The values before the ergonomics check: no cell cap, landscape fills the pane. The reference the layout tests and goldens are recorded under. */
        val Starting = PlayTuning(
            phoneCap = 552.dp,
            tabletPortraitCap = 552.dp,
            tabletLandscapeCap = null,
            landscapeControls = LandscapeControls.UnderBoard,
        )
    }
}
