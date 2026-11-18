package com.ivandsky.puzzlebox.sample

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag

fun debugOnly(flags: ReleaseFlags) = flags.isEnabled(ReleaseFlag.DebugOnly)
