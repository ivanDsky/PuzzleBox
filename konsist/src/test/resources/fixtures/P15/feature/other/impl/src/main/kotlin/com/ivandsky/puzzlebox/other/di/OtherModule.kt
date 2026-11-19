package com.ivandsky.puzzlebox.other.di

import com.ivandsky.puzzlebox.common.di.flagSet
import com.ivandsky.puzzlebox.other.flags.OtherFlag
import org.koin.dsl.module

/** Owner "twin" is registered by :feature:twin:impl too. */
val otherModule = module {
    flagSet("twin", OtherFlag.entries)
}
