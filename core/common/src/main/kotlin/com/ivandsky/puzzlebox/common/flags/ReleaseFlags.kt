package com.ivandsky.puzzlebox.common.flags

import kotlinx.coroutines.flow.StateFlow

/**
 * The one source of release-flag values (ADR-0022), bound per build type by `:app`. Gates
 * read [enabled]: ViewModels and composables collect it, so a debug override applies live
 * without a restart; one-shot code checks [isEnabled].
 */
interface ReleaseFlags {
    /** The flags that are on right now. Constant in release builds. */
    val enabled: StateFlow<Set<ReleaseFlagKey>>
}

fun ReleaseFlags.isEnabled(flag: ReleaseFlagKey): Boolean = flag in enabled.value
