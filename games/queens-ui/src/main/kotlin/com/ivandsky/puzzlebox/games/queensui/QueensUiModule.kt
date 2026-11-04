package com.ivandsky.puzzlebox.games.queensui

import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import org.koin.core.module.Module
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module

/** Koin wiring (ADR-0001 aggregation): `app` collects this with the other ui modules.
 *  Qualified by game id (the plugin binding's pattern) so the three play UIs coexist. */
val queensUiModule: Module = module {
    single<GamePlayUi>(qualifier = qualifier(QueensDefinition.id.value)) { QueensPlayUi() }
}
