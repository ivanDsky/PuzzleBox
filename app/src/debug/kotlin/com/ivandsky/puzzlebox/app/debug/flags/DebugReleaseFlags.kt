package com.ivandsky.puzzlebox.app.debug.flags

import android.content.Context
import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Debug builds' [ReleaseFlags] (ADR-0022): a flag is its local override when one is set, else
 * its release default, so a debug build behaves like release until a developer forces a flag
 * in Catalog → Release flags. Overrides persist in SharedPreferences like the other Catalog
 * settings (:app declares no DataStore), each under its flag's [ReleaseFlagKey.key]; an override
 * for a flag no longer [declared] is ignored. Release compiles none of this.
 *
 * @property declared every flag the app declares, in the Release flags section's order.
 */
internal class DebugReleaseFlags(context: Context, val declared: List<ReleaseFlagKey>) : ReleaseFlags {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)

    private val overrideState = MutableStateFlow(readOverrides())
    private val enabledState = MutableStateFlow(resolve(overrideState.value))

    /** The forced flags and their values; a flag missing here follows its release default. */
    val overrides: StateFlow<Map<ReleaseFlagKey, Boolean>> = overrideState.asStateFlow()

    override val enabled: StateFlow<Set<ReleaseFlagKey>> = enabledState.asStateFlow()

    /** Forces [flag] to [value]; `null` drops the override back to the release default. */
    fun setOverride(flag: ReleaseFlagKey, value: Boolean?) {
        prefs.edit().apply {
            if (value == null) remove(flag.key) else putBoolean(flag.key, value)
        }.apply()
        val next = if (value == null) overrideState.value - flag else overrideState.value + (flag to value)
        overrideState.value = next
        enabledState.value = resolve(next)
    }

    private fun readOverrides(): Map<ReleaseFlagKey, Boolean> = declared
        .filter { prefs.contains(it.key) }
        .associateWith { prefs.getBoolean(it.key, it.releaseDefault) }

    private fun resolve(overrides: Map<ReleaseFlagKey, Boolean>): Set<ReleaseFlagKey> =
        declared.filterTo(mutableSetOf()) { overrides[it] ?: it.releaseDefault }

    private companion object {
        const val PREFERENCES_FILE = "release_flags"
    }
}
