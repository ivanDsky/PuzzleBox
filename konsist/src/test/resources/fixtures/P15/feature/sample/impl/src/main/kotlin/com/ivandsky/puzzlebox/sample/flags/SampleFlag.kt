package com.ivandsky.puzzlebox.sample.flags

import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey

/** Registered in its own module's main `di` package. */
enum class SampleFlag(override val label: String, override val releaseDefault: Boolean) : ReleaseFlagKey {
    SampleOn(label = "Registered by its owner", releaseDefault = false),
    ;

    override val key: String get() = name
}
