package com.ivandsky.puzzlebox.sample.di

import com.ivandsky.puzzlebox.common.di.flagSet
import com.ivandsky.puzzlebox.sample.flags.DebugOnlyFlag
import org.koin.dsl.module

/** A debug-only registration: a subject, but release never loads it. */
val debugModule = module {
    flagSet("debug-only", DebugOnlyFlag.entries)
}
