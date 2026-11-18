package com.ivandsky.puzzlebox.common.flags

/** What every flag enum implements; an interface has no entries, so it is not a subject. */
interface ReleaseFlagKey {
    val key: String
    val label: String
    val releaseDefault: Boolean
}
