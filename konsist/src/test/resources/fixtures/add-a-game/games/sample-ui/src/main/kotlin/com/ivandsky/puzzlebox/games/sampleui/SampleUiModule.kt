package com.ivandsky.puzzlebox.games.sampleui

import com.ivandsky.puzzlebox.game.GamePlayUi
import com.ivandsky.puzzlebox.games.sample.SampleDefinition
import org.koin.core.module.Module
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module

/** Qualified by game id so every game's play UI coexists in one Koin graph. */
val sampleUiModule: Module = module {
    single<GamePlayUi>(qualifier = qualifier(SampleDefinition.id.value)) { SamplePlayUi() }
}
