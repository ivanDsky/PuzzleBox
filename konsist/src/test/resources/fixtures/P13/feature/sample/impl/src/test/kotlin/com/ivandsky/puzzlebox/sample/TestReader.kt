package com.ivandsky.puzzlebox.sample

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag

fun testOnly(flags: ReleaseFlags) = flags.isEnabled(ReleaseFlag.TestOnly)
