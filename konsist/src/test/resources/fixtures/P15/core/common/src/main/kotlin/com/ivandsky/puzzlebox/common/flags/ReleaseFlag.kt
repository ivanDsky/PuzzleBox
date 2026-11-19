package com.ivandsky.puzzlebox.common.flags

/** The frozen central enum: it holds no frozen entry, the added one is not allowed, and SheetScreens is gone. Registered by :app. */
enum class ReleaseFlag(override val label: String, override val releaseDefault: Boolean) : ReleaseFlagKey {
    AddedLater(label = "Belongs in its owner's enum", releaseDefault = false),
    ;

    override val key: String get() = name
}
