package com.ivandsky.puzzlebox.domain.di

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.domain.Clean

// The Koin module counts too: wiring is where a flag read hides.
fun clean(flags: ReleaseFlags?) = Clean(polish = flags?.isEnabled(ReleaseFlag.Polish) == true)
