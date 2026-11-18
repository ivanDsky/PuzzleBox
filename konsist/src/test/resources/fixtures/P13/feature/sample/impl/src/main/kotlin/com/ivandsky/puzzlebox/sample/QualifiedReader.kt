package com.ivandsky.puzzlebox.sample

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag

fun qualified(flags: ReleaseFlags) = flags.isEnabled(ReleaseFlag.Qualified)
