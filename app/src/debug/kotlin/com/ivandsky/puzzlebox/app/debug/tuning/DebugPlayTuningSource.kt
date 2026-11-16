package com.ivandsky.puzzlebox.app.debug.tuning

import android.content.Context
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.gameui.DeviceClass
import com.ivandsky.puzzlebox.gameui.LandscapeControls
import com.ivandsky.puzzlebox.gameui.PlayTuning
import com.ivandsky.puzzlebox.gameui.PlayTuningSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Debug builds' play tuning (nav-rethink B6): the board caps and the landscape controls placement a developer tunes
 * on a device. Values persist in SharedPreferences like the other debug settings (:app declares no DataStore) and
 * [tuning] emits on every change, so an open play screen follows live. A value never stored is [PlayTuning.Starting]'s;
 * caps are held to [MIN_CAP_DP]..[MAX_CAP_DP]. The flag-aware source wraps this one as its base, so the values apply
 * only while BoardErgonomics is on. Release compiles none of this.
 */
internal class DebugPlayTuningSource(context: Context) : PlayTuningSource {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)

    private val state = MutableStateFlow(read())

    /** Never null: this source holds a tuning; the flag decides whether it reaches play. */
    override val tuning: StateFlow<PlayTuning> = state.asStateFlow()

    /** Sets the board cap of [deviceClass] to [cap], clamped to the range; the other classes are untouched. */
    fun setCap(deviceClass: DeviceClass, cap: Dp) {
        val clamped = cap.clamped()
        prefs.edit().putInt(capKey(deviceClass), clamped.value.toInt()).apply()
        state.value = when (deviceClass) {
            DeviceClass.Phone -> state.value.copy(phoneCap = clamped)
            DeviceClass.TabletPortrait -> state.value.copy(tabletPortraitCap = clamped)
            DeviceClass.TabletLandscape -> state.value.copy(tabletLandscapeCap = clamped)
        }
    }

    /** Drops the tablet landscape cap: the board fills its pane. A later [setCap] for it brings a cap back. */
    fun fillTabletLandscapePane() {
        prefs.edit().putInt(capKey(DeviceClass.TabletLandscape), FILL_THE_PANE).apply()
        state.value = state.value.copy(tabletLandscapeCap = null)
    }

    fun setLandscapeControls(controls: LandscapeControls) {
        prefs.edit().putString(KEY_LANDSCAPE_CONTROLS, controls.name).apply()
        state.value = state.value.copy(landscapeControls = controls)
    }

    /** Forgets every stored value: back to [PlayTuning.Starting]. */
    fun reset() {
        prefs.edit().clear().apply()
        state.value = PlayTuning.Starting
    }

    private fun read(): PlayTuning {
        val start = PlayTuning.Starting
        return PlayTuning(
            phoneCap = readCap(DeviceClass.Phone, start.phoneCap) ?: start.phoneCap,
            tabletPortraitCap = readCap(DeviceClass.TabletPortrait, start.tabletPortraitCap) ?: start.tabletPortraitCap,
            tabletLandscapeCap = readCap(DeviceClass.TabletLandscape, start.tabletLandscapeCap),
            landscapeControls = LandscapeControls.entries
                .firstOrNull { it.name == prefs.getString(KEY_LANDSCAPE_CONTROLS, null) } ?: start.landscapeControls,
        )
    }

    /** The stored cap of [deviceClass], [FILL_THE_PANE] as null, else [default] when nothing is stored. */
    private fun readCap(deviceClass: DeviceClass, default: Dp?): Dp? {
        val stored = prefs.getInt(capKey(deviceClass), NOT_STORED)
        return when (stored) {
            NOT_STORED -> default
            FILL_THE_PANE -> null
            else -> stored.dp.clamped()
        }
    }

    private fun capKey(deviceClass: DeviceClass): String = when (deviceClass) {
        DeviceClass.Phone -> "phone_cap_dp"
        DeviceClass.TabletPortrait -> "tablet_portrait_cap_dp"
        DeviceClass.TabletLandscape -> "tablet_landscape_cap_dp"
    }

    companion object {
        const val MIN_CAP_DP = 240
        const val MAX_CAP_DP = 960
        const val STEP_DP = 8

        private const val PREFERENCES_FILE = "play_tuning"
        private const val KEY_LANDSCAPE_CONTROLS = "landscape_controls"
        private const val NOT_STORED = Int.MIN_VALUE
        private const val FILL_THE_PANE = -1

        private fun Dp.clamped(): Dp = value.coerceIn(MIN_CAP_DP.toFloat(), MAX_CAP_DP.toFloat()).dp
    }
}
