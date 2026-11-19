package com.ivandsky.puzzlebox.common.flags

interface ReleaseFlags {
    val enabled: Set<ReleaseFlag>
}

fun ReleaseFlags.isEnabled(flag: ReleaseFlag): Boolean = flag in enabled

enum class ReleaseFlag { Polish }
