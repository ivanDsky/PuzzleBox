package com.ivandsky.puzzlebox.common.flags

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Release builds' flags: every [declared] flag's [ReleaseFlagKey.releaseDefault], with no override path (ADR-0022). */
class ShippedReleaseFlags(declared: List<ReleaseFlagKey>) : ReleaseFlags {
    override val enabled: StateFlow<Set<ReleaseFlagKey>> =
        MutableStateFlow(declared.filterTo(mutableSetOf()) { it.releaseDefault }).asStateFlow()
}
