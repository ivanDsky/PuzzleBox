package com.ivandsky.puzzlebox.common.flags

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Release builds' flags: every [ReleaseFlag.releaseDefault], with no override path (ADR-0022). */
object ShippedReleaseFlags : ReleaseFlags {
    override val enabled: StateFlow<Set<ReleaseFlag>> =
        MutableStateFlow(ReleaseFlag.entries.filterTo(mutableSetOf()) { it.releaseDefault }).asStateFlow()
}
