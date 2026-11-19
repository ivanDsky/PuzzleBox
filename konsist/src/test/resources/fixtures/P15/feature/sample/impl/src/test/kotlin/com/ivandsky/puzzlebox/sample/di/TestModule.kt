package com.ivandsky.puzzlebox.sample.di

import com.ivandsky.puzzlebox.common.di.flagSet
import com.ivandsky.puzzlebox.sample.flags.UnregisteredFlag
import org.koin.dsl.module

/** Test sources are not scanned: no subject, and UnregisteredFlag stays unregistered. */
val testModule = module {
    flagSet("unregistered", UnregisteredFlag.entries)
}
