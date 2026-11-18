package com.ivandsky.puzzlebox.sample

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag.Imported

fun imported(flags: ReleaseFlags) = flags.isEnabled(Imported)
