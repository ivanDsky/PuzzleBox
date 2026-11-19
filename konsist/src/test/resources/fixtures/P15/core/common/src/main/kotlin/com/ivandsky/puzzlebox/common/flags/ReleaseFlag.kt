package com.ivandsky.puzzlebox.common.flags

/** The frozen central enum: it takes no entry, so the added one is not allowed. Registered by :app. */
enum class ReleaseFlag(override val label: String, override val releaseDefault: Boolean) : ReleaseFlagKey {
    AddedLater(label = "Belongs in its owner's enum", releaseDefault = false),
    ;

    override val key: String get() = name
}
