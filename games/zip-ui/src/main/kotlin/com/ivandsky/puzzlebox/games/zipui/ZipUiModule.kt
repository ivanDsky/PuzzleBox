package com.ivandsky.puzzlebox.games.zipui

import org.koin.core.module.Module
import org.koin.dsl.module

/** Koin wiring (ADR-0001 aggregation): `app` collects this with the other ui modules. */
val zipUiModule: Module = module {
    factory { ZipBoardRenderer() }
}
