package com.ivandsky.puzzlebox.common.di

import com.ivandsky.puzzlebox.common.flags.FlagSet
import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey
import org.koin.core.module.Module
import org.koin.core.qualifier.named

/** The declaration is no call, so it is neither a subject nor a malformed call. */
fun Module.flagSet(owner: String, flags: List<ReleaseFlagKey>) {
    single(named("flags.$owner")) { FlagSet(owner, flags) }
}
