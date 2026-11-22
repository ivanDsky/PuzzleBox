package com.ivandsky.puzzlebox.play.di

import com.ivandsky.puzzlebox.play.CustomGameViewModel
import com.ivandsky.puzzlebox.play.GameViewModel
import com.ivandsky.puzzlebox.play.HowToPlayRules
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.GameKey
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The live play's wiring, included by [playModule]: the game's and the custom game's view models and the rules sheet's copy. */
val gameModule = module {
    viewModel { (key: GameKey) -> GameViewModel(key, get(), get(), get(), get()) }
    viewModel { (key: CustomGameKey) -> CustomGameViewModel(key, get(), get(), get()) }
    // How to play opens the rules sheet over the live play for a game with rules copy.
    single { HowToPlayRules() }
}
