package com.ivandsky.puzzlebox.sample.flags

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag

/** Owner enums found through a fully qualified ReleaseFlagKey, and through the interface's final name ReleaseFlag. */
enum class QualifiedKeyFlag(
    override val label: String,
    override val releaseDefault: Boolean,
) : com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey {
    QualifiedKeyUnread(label = "Read nowhere", releaseDefault = true),
}

enum class RenamedFlag(override val label: String, override val releaseDefault: Boolean) : ReleaseFlag {
    RenamedUnread(label = "Read nowhere", releaseDefault = true),
}
