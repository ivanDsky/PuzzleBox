package com.ivandsky.puzzlebox.app.di

import com.ivandsky.puzzlebox.common.di.flagSet
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.sample.flags.SampleFlags
import org.koin.dsl.module

/** :app registers the central enum and, with named arguments and a qualified reference, a nested owner enum. */
val appModule = module {
    flagSet("common", ReleaseFlag.entries)
    flagSet(owner = "nested", flags = SampleFlags.Flag.entries)
}
