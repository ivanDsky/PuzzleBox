package com.ivandsky.puzzlebox.twin.flags

import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey

/** Registered by its owner; the repeated owner name is the registrations' violation, not the enum's. */
enum class TwinFlag(override val label: String, override val releaseDefault: Boolean) : ReleaseFlagKey {
    TwinOn(label = "Registered as twin", releaseDefault = false),
    ;

    override val key: String get() = name
}
