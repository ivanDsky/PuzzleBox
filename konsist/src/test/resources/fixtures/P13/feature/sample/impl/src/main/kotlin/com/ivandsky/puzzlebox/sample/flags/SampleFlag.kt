package com.ivandsky.puzzlebox.sample.flags

import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey

enum class SampleFlag(override val label: String, override val releaseDefault: Boolean) : ReleaseFlagKey {
    OwnerRead(label = "Read as SampleFlag.OwnerRead", releaseDefault = true),
    OwnerUnread(label = "Read nowhere", releaseDefault = true),
}
