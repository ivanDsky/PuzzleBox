package com.ivandsky.puzzlebox.play.di

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.domain.random.LoadRandomDay
import com.ivandsky.puzzlebox.play.domain.random.LoadRandomPuzzle
import com.ivandsky.puzzlebox.play.domain.random.LoadRandomShared
import com.ivandsky.puzzlebox.play.random.RandomPickMemory
import com.ivandsky.puzzlebox.play.random.RandomPuzzleViewModel
import kotlin.random.Random
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The random pick's wiring, included by [playModule]: [LoadRandomDay], [LoadRandomShared] and [LoadRandomPuzzle], which
 * shuffle with [Random.Default], the memory of the last pick and the sheet's view model.
 */
val randomModule = module {
    factory {
        LoadRandomDay(
            daily = get(),
            completions = get(),
            loadPastDay = get(),
            registry = get(),
            random = Random.Default,
        )
    }
    factory { LoadRandomShared(list = get(), random = Random.Default) }
    factory { LoadRandomPuzzle(day = get(), shared = get()) }
    // The memory forgets the last pick with the process.
    single { RandomPickMemory() }
    // The preset game is the one parameter: the page whose Random opened the sheet.
    viewModel { params ->
        RandomPuzzleViewModel(params.get<GameId>(), get(), get(), get())
    }
}
