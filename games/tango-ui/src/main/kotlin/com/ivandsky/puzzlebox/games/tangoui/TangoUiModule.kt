package com.ivandsky.puzzlebox.games.tangoui

import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import org.koin.core.module.Module
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module

/** Koin wiring (ADR-0001 aggregation): `app` collects this with the other ui modules.
 *  Qualified by game id (the plugin binding's pattern) so the three play UIs coexist. */
val tangoUiModule: Module = module {
    single<GamePlayUi>(qualifier = qualifier(TangoDefinition.id.value)) { TangoPlayUi() }
}
