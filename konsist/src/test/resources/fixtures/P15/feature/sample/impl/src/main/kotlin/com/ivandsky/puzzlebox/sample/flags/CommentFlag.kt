package com.ivandsky.puzzlebox.sample.flags

import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey

/** Registered only in a comment and a string. */
enum class CommentFlag(override val label: String, override val releaseDefault: Boolean) : ReleaseFlagKey {
    Commented(label = "flagSet(\"comment\", CommentFlag.entries)", releaseDefault = false),
    ;

    override val key: String get() = name
}
