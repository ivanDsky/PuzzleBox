package com.ivandsky.puzzlebox.twin.di

import com.ivandsky.puzzlebox.common.di.flagSet
import com.ivandsky.puzzlebox.twin.flags.TwinFlag
import org.koin.dsl.module

/** Owner "twin" is registered by :feature:other:impl too. */
val twinModule = module {
    flagSet("twin", TwinFlag.entries)
}
