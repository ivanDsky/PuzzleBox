package com.ivandsky.puzzlebox.app.di

import com.ivandsky.puzzlebox.app.debug.flags.DebugReleaseFlags
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/** Debug flags: release defaults plus the Catalog's local overrides (ADR-0022). */
val buildTypeReleaseFlagsModule: Module = module {
    single { DebugReleaseFlags(androidContext()) } bind ReleaseFlags::class
}
