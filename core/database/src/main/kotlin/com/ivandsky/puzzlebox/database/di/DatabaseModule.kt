package com.ivandsky.puzzlebox.database.di

import android.content.Context
import androidx.room.Room
import com.ivandsky.puzzlebox.database.Migrations
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import org.koin.dsl.module

/** The one on-device Room database and its DAOs; every schema change ships a Migration. */
val databaseModule = module {
    single {
        Room.databaseBuilder(get<Context>(), PuzzleBoxDatabase::class.java, PuzzleBoxDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .build()
    }
    single { get<PuzzleBoxDatabase>().puzzleDailyDao() }
    single { get<PuzzleBoxDatabase>().completionDao() }
    single { get<PuzzleBoxDatabase>().savedPlayDao() }
    single { get<PuzzleBoxDatabase>().customSavedPlayDao() }
    single { get<PuzzleBoxDatabase>().draftDao() }
}
