package com.ivandsky.puzzlebox.sample.flags

import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey

/** An owner enum nested in an object. */
object SampleFlags {
    enum class Flag(override val label: String, override val releaseDefault: Boolean) : ReleaseFlagKey {
        NestedRead(label = "Read through an import of SampleFlags.Flag.NestedRead", releaseDefault = true),
        NestedUnread(label = "Read nowhere", releaseDefault = true),
    }
}
