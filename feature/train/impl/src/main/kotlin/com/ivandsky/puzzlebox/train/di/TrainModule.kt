package com.ivandsky.puzzlebox.train.di

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.gameui.trainer.TrainerGame
import com.ivandsky.puzzlebox.train.domain.RecordAnswer
import com.ivandsky.puzzlebox.train.practice.AndroidScreenReaderCheck
import com.ivandsky.puzzlebox.train.practice.PracticeViewModel
import com.ivandsky.puzzlebox.train.practice.ScreenReaderCheck
import com.ivandsky.puzzlebox.train.tab.TrainViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Train feature wiring: the answer use case, the screen-reader check the board end asks, the pane's view model (the game
 * row, the technique list, the open technique) and the practice board's, one per game and technique, which takes its
 * [TrainerGame] and technique id as parameters. The Trainer registry (the games that have a trainer), the progress store,
 * the puzzle source and the archive a tap fetches from are bound by the app and the core modules, so this module never
 * names a game.
 */
val trainModule: Module = module {
    factory { RecordAnswer(progress = get()) }
    factory<ScreenReaderCheck> { AndroidScreenReaderCheck(androidContext()) }
    viewModel { TrainViewModel(registry = get(), prefs = get(), progress = get(), savedState = get()) }
    viewModel { params ->
        PracticeViewModel(
            game = params.get<TrainerGame>(),
            technique = params.get<TechniqueId>(),
            source = get(),
            archive = get(),
            store = get(),
            recordAnswer = get(),
            dispatchers = get(),
            screenReader = get(),
        )
    }
}
