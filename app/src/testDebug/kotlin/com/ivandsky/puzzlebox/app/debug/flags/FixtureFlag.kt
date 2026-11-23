package com.ivandsky.puzzlebox.app.debug.flags

import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey

/**
 * A test-only flag enum for the debug override store and its panel, which need keys to store and show while no feature
 * declares a live release flag (ADR-0022). It is never registered as a [com.ivandsky.puzzlebox.common.flags.FlagSet].
 */
internal enum class FixtureFlag(override val label: String, override val releaseDefault: Boolean) : ReleaseFlagKey {
    DarkByDefault(label = "Dark by default", releaseDefault = false),
    OnInRelease(label = "On in release", releaseDefault = true),
    ;

    override val key: String get() = name
}
