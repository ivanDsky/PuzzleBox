package com.ivandsky.puzzlebox.train.di

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.gameui.trainer.TrainerGame
import com.ivandsky.puzzlebox.train.domain.RecordAnswer
import com.ivandsky.puzzlebox.train.drill.DrillViewModel
import com.ivandsky.puzzlebox.train.round.RoundViewModel
import com.ivandsky.puzzlebox.train.tab.TrainViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Train feature wiring: the answer use case, the pane's view model (the game row, the technique list, the open
 * technique), the round's and the drill's, one per game and technique, which take their [TrainerGame] and technique id as
 * parameters. The Trainer registry (the games that have a trainer), the progress store, the puzzle source, the archive a
 * tap fetches from and the play registry the drill's board comes from are bound by the app and the core modules, so this
 * module never names a game.
 */
val trainModule: Module = module {
    factory { RecordAnswer(progress = get()) }
    viewModel { TrainViewModel(registry = get(), prefs = get(), progress = get(), savedState = get()) }
    viewModel { params ->
        RoundViewModel(
            game = params.get<TrainerGame>(),
            technique = params.get<TechniqueId>(),
            source = get(),
            archive = get(),
            recordAnswer = get(),
            dispatchers = get(),
        )
    }
    viewModel { params ->
        DrillViewModel(
            game = params.get<TrainerGame>(),
            technique = params.get<TechniqueId>(),
            plays = get(),
            games = get(),
            archive = get(),
            dispatchers = get(),
        )
    }
}
