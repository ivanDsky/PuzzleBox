package com.ivandsky.puzzlebox.sample.flags

import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey

/** Registered only in src/debug: release would read it as off. */
enum class DebugOnlyFlag(override val label: String, override val releaseDefault: Boolean) : ReleaseFlagKey {
    DebugOnly(label = "Debug builds only", releaseDefault = false),
    ;

    override val key: String get() = name
}
