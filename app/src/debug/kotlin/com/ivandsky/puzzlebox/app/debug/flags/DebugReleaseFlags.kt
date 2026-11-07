package com.ivandsky.puzzlebox.app.debug.flags

import android.content.Context
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Debug builds' [ReleaseFlags] (ADR-0022): a flag is its local override when one is set, else
 * its release default, so a debug build behaves like release until a developer forces a flag
 * in Catalog → Release flags. Overrides persist in SharedPreferences like the other Catalog
 * settings (:app declares no DataStore); an override for a deleted flag is ignored. Release
 * compiles none of this.
 */
internal class DebugReleaseFlags(context: Context) : ReleaseFlags {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)

    private val overrideState = MutableStateFlow(readOverrides())
    private val enabledState = MutableStateFlow(resolve(overrideState.value))

    /** The forced flags and their values; a flag missing here follows its release default. */
    val overrides: StateFlow<Map<ReleaseFlag, Boolean>> = overrideState.asStateFlow()

    override val enabled: StateFlow<Set<ReleaseFlag>> = enabledState.asStateFlow()

    /** Forces [flag] to [value]; `null` drops the override back to the release default. */
    fun setOverride(flag: ReleaseFlag, value: Boolean?) {
        prefs.edit().apply {
            if (value == null) remove(flag.name) else putBoolean(flag.name, value)
        }.apply()
        val next = if (value == null) overrideState.value - flag else overrideState.value + (flag to value)
        overrideState.value = next
        enabledState.value = resolve(next)
    }

    private fun readOverrides(): Map<ReleaseFlag, Boolean> = ReleaseFlag.entries
        .filter { prefs.contains(it.name) }
        .associateWith { prefs.getBoolean(it.name, it.releaseDefault) }

    private fun resolve(overrides: Map<ReleaseFlag, Boolean>): Set<ReleaseFlag> =
        ReleaseFlag.entries.filterTo(mutableSetOf()) { overrides[it] ?: it.releaseDefault }

    private companion object {
        const val PREFERENCES_FILE = "release_flags"
    }
}
