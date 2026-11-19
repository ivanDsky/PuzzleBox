package com.ivandsky.puzzlebox.common.flags

/** The frozen central enum: its frozen entries are fine, the added one is not, and SheetScreens is gone. Registered by :app. */
enum class ReleaseFlag(override val label: String, override val releaseDefault: Boolean) : ReleaseFlagKey {
    ResultHeroPolish(label = "Frozen", releaseDefault = true),
    HintPolish(label = "Frozen", releaseDefault = true),
    RanksFreshness(label = "Frozen", releaseDefault = true),
    ZipDotPulse(label = "Frozen", releaseDefault = true),
    AddedLater(label = "Belongs in its owner's enum", releaseDefault = false),
    ;

    override val key: String get() = name
}
