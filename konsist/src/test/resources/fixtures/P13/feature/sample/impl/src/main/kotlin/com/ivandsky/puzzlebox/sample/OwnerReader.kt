package com.ivandsky.puzzlebox.sample

import com.ivandsky.puzzlebox.sample.flags.SampleFlag

fun ownerRead(flags: ReleaseFlags) = flags.isEnabled(SampleFlag.OwnerRead)
