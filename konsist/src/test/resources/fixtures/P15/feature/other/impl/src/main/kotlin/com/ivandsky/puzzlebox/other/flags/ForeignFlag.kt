package com.ivandsky.puzzlebox.other.flags

import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey

/** Registered only by another feature module, which the app may not load. */
enum class ForeignFlag(override val label: String, override val releaseDefault: Boolean) : ReleaseFlagKey {
    Foreign(label = "Registered by :feature:sample:impl", releaseDefault = false),
    ;

    override val key: String get() = name
}
