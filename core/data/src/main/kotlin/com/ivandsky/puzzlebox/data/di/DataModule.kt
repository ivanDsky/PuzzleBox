package com.ivandsky.puzzlebox.data.di

import android.content.Context
import androidx.room.Room
import com.ivandsky.puzzlebox.data.db.Migrations
import com.ivandsky.puzzlebox.data.db.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.data.network.AuthHeaderSource
import com.ivandsky.puzzlebox.data.network.LINKEDIN_BASE_URL
import com.ivandsky.puzzlebox.data.network.PuzzleFeedApi
import com.ivandsky.puzzlebox.data.network.okHttpClient
import com.ivandsky.puzzlebox.data.network.retrofit
import com.ivandsky.puzzlebox.data.repository.DailyPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.OfflineFirstDailyPuzzleRepository
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import retrofit2.Retrofit

val dataModule = module {
    single { Json { ignoreUnknownKeys = true } }
    single<AuthHeaderSource> { AuthHeaderSource.NONE }
    single { okHttpClient(authSource = get()) }
    single { retrofit(baseUrl = LINKEDIN_BASE_URL, client = get(), json = get()) }
    single { get<Retrofit>().create(PuzzleFeedApi::class.java) }
    single {
        Room.databaseBuilder(get<Context>(), PuzzleBoxDatabase::class.java, PuzzleBoxDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .build()
    }
    single { get<PuzzleBoxDatabase>().dailyPuzzleDao() }
    single<DailyPuzzleRepository> { OfflineFirstDailyPuzzleRepository(get(), get(), get()) }
}
