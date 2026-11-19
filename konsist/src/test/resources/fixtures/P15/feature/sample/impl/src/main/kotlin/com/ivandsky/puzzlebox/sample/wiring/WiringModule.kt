package com.ivandsky.puzzlebox.sample.wiring

import com.ivandsky.puzzlebox.common.di.flagSet
import com.ivandsky.puzzlebox.sample.flags.NotDiFlag
import org.koin.dsl.module

/** A well-formed call outside a `di` package: a subject, but no registration. */
val wiringModule = module {
    flagSet("not-di", NotDiFlag.entries)
}
