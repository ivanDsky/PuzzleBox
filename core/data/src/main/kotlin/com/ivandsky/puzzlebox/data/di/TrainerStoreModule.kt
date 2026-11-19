package com.ivandsky.puzzlebox.data.di

import com.ivandsky.puzzlebox.data.trainer.CachedDailyTrainerPuzzleSource
import com.ivandsky.puzzlebox.data.trainer.RoomTrainerProgressRepository
import com.ivandsky.puzzlebox.data.trainer.TrainerProgressRepository
import com.ivandsky.puzzlebox.data.trainer.TrainerPuzzleSource
import org.koin.dsl.module

/** The trainer's progress store and the puzzles a round is cut from. */
val trainerStoreModule = module {
    single<TrainerProgressRepository> { RoomTrainerProgressRepository(get(), get()) }
    single<TrainerPuzzleSource> { CachedDailyTrainerPuzzleSource(get()) }
}
