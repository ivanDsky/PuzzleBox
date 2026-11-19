package com.ivandsky.puzzlebox.other.flags

import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey

/** Registered by its owner, under an owner name :feature:twin:impl uses too. */
enum class OtherFlag(override val label: String, override val releaseDefault: Boolean) : ReleaseFlagKey {
    OtherOn(label = "Registered as twin", releaseDefault = false),
    ;

    override val key: String get() = name
}
