package com.ivandsky.puzzlebox.data.di

import com.ivandsky.puzzlebox.data.trainer.InMemoryTrainerProgressRepository
import com.ivandsky.puzzlebox.data.trainer.TrainerProgressRepository
import org.koin.dsl.module

/** The one binding of the trainer's progress store. */
val trainerStoreModule = module {
    single<TrainerProgressRepository> { InMemoryTrainerProgressRepository() }
}
