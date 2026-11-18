package com.ivandsky.puzzlebox.sample

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag as Flag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag.Aliased as Renamed

fun aliased(flags: ReleaseFlags) = flags.isEnabled(Renamed) && flags.isEnabled(Flag.ClassAliased)
