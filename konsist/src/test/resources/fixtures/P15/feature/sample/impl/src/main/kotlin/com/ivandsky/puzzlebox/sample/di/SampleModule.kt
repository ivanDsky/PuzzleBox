package com.ivandsky.puzzlebox.sample.di

import com.ivandsky.puzzlebox.common.di.flagSet
import com.ivandsky.puzzlebox.other.flags.ForeignFlag
import com.ivandsky.puzzlebox.sample.flags.SampleFlag
import org.koin.dsl.module

/** Registers its own enum; another feature module's enum registered here does not count. */
val sampleModule = module {
    flagSet("sample", SampleFlag.entries)
    // flagSet("comment", CommentFlag.entries)
    flagSet("foreign", ForeignFlag.entries)
}
