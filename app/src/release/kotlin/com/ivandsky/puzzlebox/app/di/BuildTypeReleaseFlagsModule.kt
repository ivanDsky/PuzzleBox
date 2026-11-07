package com.ivandsky.puzzlebox.app.di

import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.ShippedReleaseFlags
import org.koin.core.module.Module
import org.koin.dsl.module

/** Release flags are their release defaults; nothing can override them (ADR-0022). */
val buildTypeReleaseFlagsModule: Module = module {
    single<ReleaseFlags> { ShippedReleaseFlags }
}
