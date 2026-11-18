package com.ivandsky.puzzlebox.sample

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag.*

fun starred(flags: ReleaseFlags) = flags.isEnabled(Starred)
