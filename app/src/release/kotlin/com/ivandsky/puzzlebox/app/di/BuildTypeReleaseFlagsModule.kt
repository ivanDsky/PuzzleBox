package com.ivandsky.puzzlebox.app.di

import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.ShippedReleaseFlags
import com.ivandsky.puzzlebox.gameui.PlayTuningSource
import com.ivandsky.puzzlebox.gameui.ShippedPlayTuningSource
import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Release flags are their release defaults; nothing can override them (ADR-0022). The play tuning is the shipped
 * one, held only while BoardErgonomics is on.
 */
val buildTypeReleaseFlagsModule: Module = module {
    single<ReleaseFlags> { ShippedReleaseFlags }
    single<PlayTuningSource> { FlaggedPlayTuningSource(flags = get(), base = ShippedPlayTuningSource, scope = get<CoroutineScope>()) }
}
