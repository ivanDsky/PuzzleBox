package com.ivandsky.puzzlebox.app.di

import com.ivandsky.puzzlebox.app.debug.flags.DebugReleaseFlags
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.gameui.PlayTuningSource
import com.ivandsky.puzzlebox.gameui.ShippedPlayTuningSource
import kotlinx.coroutines.CoroutineScope
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Debug flags: release defaults plus the Catalog's local overrides (ADR-0022). The play tuning is held only while
 * BoardErgonomics is on; its `base` is the one line the settings-backed source replaces (nav-rethink ticket 53).
 */
val buildTypeReleaseFlagsModule: Module = module {
    single { DebugReleaseFlags(androidContext()) } bind ReleaseFlags::class
    single<PlayTuningSource> { FlaggedPlayTuningSource(flags = get(), base = ShippedPlayTuningSource, scope = get<CoroutineScope>()) }
}
