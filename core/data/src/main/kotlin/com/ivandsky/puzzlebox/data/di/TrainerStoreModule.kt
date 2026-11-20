package com.ivandsky.puzzlebox.data.di

import com.ivandsky.puzzlebox.data.trainer.ArchiveTrainerPuzzleSource
import com.ivandsky.puzzlebox.data.trainer.CachedTrainerPuzzleSource
import com.ivandsky.puzzlebox.data.trainer.RoomTrainerProgressRepository
import com.ivandsky.puzzlebox.data.trainer.TrainerArchive
import com.ivandsky.puzzlebox.data.trainer.TrainerProgressRepository
import com.ivandsky.puzzlebox.data.trainer.TrainerPuzzleSource
import com.ivandsky.puzzlebox.game.DailySource
import org.koin.dsl.module

/** The trainer's progress store, the puzzles a round is cut from, and the archive a tap fetches one from. */
val trainerStoreModule = module {
    single<TrainerProgressRepository> { RoomTrainerProgressRepository(get(), get()) }
    single<TrainerPuzzleSource> { CachedTrainerPuzzleSource(daily = get(), trainerPuzzles = get(), registry = get()) }
    single<TrainerArchive> {
        ArchiveTrainerPuzzleSource(
            sources = getAll<DailySource>().associateBy { it.gameId },
            registry = get(),
            dao = get(),
            budget = get(),
            dispatchers = get(),
            clock = System::currentTimeMillis,
        )
    }
}
