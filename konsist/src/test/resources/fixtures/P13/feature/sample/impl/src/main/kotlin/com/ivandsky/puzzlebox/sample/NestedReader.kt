package com.ivandsky.puzzlebox.sample

import com.ivandsky.puzzlebox.sample.flags.SampleFlags.Flag.NestedRead

fun nestedRead(flags: ReleaseFlags) = flags.isEnabled(NestedRead)
