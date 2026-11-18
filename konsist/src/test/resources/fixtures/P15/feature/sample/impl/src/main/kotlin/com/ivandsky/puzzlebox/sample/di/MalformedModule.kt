package com.ivandsky.puzzlebox.sample.di

import com.ivandsky.puzzlebox.common.di.flagSet
import com.ivandsky.puzzlebox.sample.flags.SampleFlag
import org.koin.dsl.module

private val owner = "sample-extra"

/** An owner held in a variable cannot be checked for uniqueness, so the call shape is the violation. */
val malformedModule = module {
    flagSet(owner, SampleFlag.entries)
}
