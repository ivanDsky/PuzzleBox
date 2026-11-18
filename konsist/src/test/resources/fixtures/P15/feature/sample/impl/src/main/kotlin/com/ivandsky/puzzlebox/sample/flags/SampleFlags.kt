package com.ivandsky.puzzlebox.sample.flags

import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey

/** A nested owner enum, registered by :app as `SampleFlags.Flag.entries`. */
object SampleFlags {
    enum class Flag(override val label: String, override val releaseDefault: Boolean) : ReleaseFlagKey {
        NestedOn(label = "Registered by :app", releaseDefault = false),
        ;

        override val key: String get() = name
    }
}
