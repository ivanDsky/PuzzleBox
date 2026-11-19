package com.ivandsky.puzzlebox.sample.flags

import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey

/** Registered outside a `di` package. */
enum class NotDiFlag(override val label: String, override val releaseDefault: Boolean) : ReleaseFlagKey {
    Wired(label = "Registered in a wiring package", releaseDefault = false),
    ;

    override val key: String get() = name
}
