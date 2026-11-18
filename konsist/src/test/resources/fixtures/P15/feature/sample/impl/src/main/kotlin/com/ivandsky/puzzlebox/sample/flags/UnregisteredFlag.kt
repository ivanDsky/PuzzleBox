package com.ivandsky.puzzlebox.sample.flags

import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey

/** Registered nowhere in production; the test-source registration does not count. */
enum class UnregisteredFlag(override val label: String, override val releaseDefault: Boolean) : ReleaseFlagKey {
    Lost(label = "Never declared", releaseDefault = false),
    ;

    override val key: String get() = name
}
