package com.ivandsky.puzzlebox.data.di

import com.ivandsky.puzzlebox.data.trainer.CachedDailyTrainerPuzzleSource
import com.ivandsky.puzzlebox.data.trainer.InMemoryTrainerProgressRepository
import com.ivandsky.puzzlebox.data.trainer.TrainerProgressRepository
import com.ivandsky.puzzlebox.data.trainer.TrainerPuzzleSource
import org.koin.dsl.module

/** The trainer's progress store and the puzzles a round is cut from. */
val trainerStoreModule = module {
    single<TrainerProgressRepository> { InMemoryTrainerProgressRepository() }
    single<TrainerPuzzleSource> { CachedDailyTrainerPuzzleSource(get()) }
}
