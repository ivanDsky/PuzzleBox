package com.ivandsky.puzzlebox.common.di

import com.ivandsky.puzzlebox.common.flags.FlagSet
import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey
import org.koin.core.module.Module
import org.koin.core.qualifier.named

/**
 * Declares [owner]'s release flags (ADR-0022): `flagSet("<owner>", <Owner>Flag.entries)` in the owner's Koin module.
 * Each set is its own `flags.<owner>` single, so one set never replaces another; a set that is never registered reads
 * as off in every build, which konsist P15 rules out.
 */
fun Module.flagSet(owner: String, flags: List<ReleaseFlagKey>) {
    single(named("flags.$owner")) { FlagSet(owner, flags) }
}
