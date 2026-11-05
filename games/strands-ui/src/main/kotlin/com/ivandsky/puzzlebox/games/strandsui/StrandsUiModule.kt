package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import org.koin.core.module.Module
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module

/** Koin wiring (ADR-0001 aggregation): `app` collects this with the other ui modules,
 *  qualified by game id so every play UI coexists. */
val strandsUiModule: Module = module {
    single<GamePlayUi>(qualifier = qualifier(StrandsDefinition.id.value)) { StrandsPlayUi() }
}
