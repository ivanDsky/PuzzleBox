package com.ivandsky.puzzlebox.games.zipui.di

import com.ivandsky.puzzlebox.common.di.flagSet
import com.ivandsky.puzzlebox.games.zipui.ZipUiFlag
import org.koin.core.module.Module
import org.koin.dsl.module

/** Declares games/zip-ui's release flags (ADR-0022); [com.ivandsky.puzzlebox.games.zipui.zipUiModule] includes it. */
internal val zipUiFlagModule: Module = module {
    flagSet("zip-ui", ZipUiFlag.entries)
}
