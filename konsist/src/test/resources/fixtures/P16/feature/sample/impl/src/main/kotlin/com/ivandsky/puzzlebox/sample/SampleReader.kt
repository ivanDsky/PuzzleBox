package com.ivandsky.puzzlebox.sample

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled

// A feature reads its flags; only :core:domain is judged.
fun polish(flags: ReleaseFlags) = flags.isEnabled(ReleaseFlag.Polish)
