package com.ivandsky.puzzlebox.games.tangoui

import org.koin.core.module.Module
import org.koin.dsl.module

/** Koin wiring (ADR-0001 aggregation): `app` collects this with the other ui modules. */
val tangoUiModule: Module = module {
    factory { TangoBoardRenderer() }
}
