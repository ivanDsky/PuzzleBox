package com.ivandsky.puzzlebox.data.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.db.Migrations
import com.ivandsky.puzzlebox.data.db.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.data.network.AuthHeaderSource
import com.ivandsky.puzzlebox.data.network.LINKEDIN_BASE_URL
import com.ivandsky.puzzlebox.data.network.PuzzleFeedApi
import com.ivandsky.puzzlebox.data.network.okHttpClient
import com.ivandsky.puzzlebox.data.network.retrofit
import com.ivandsky.puzzlebox.data.repository.DailyPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.DataStoreDebugStyleRepository
import com.ivandsky.puzzlebox.data.repository.DataStoreUserPreferencesRepository
import com.ivandsky.puzzlebox.data.repository.DebugStyleRepository
import com.ivandsky.puzzlebox.data.repository.OfflineFirstDailyPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
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
    // The DataStore owns its file for the whole process, so its scope lives as long
    // as the singleton: I/O dispatcher from the provider, SupervisorJob so one failed
    // write never cancels the scope.
    single {
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(get<DispatcherProvider>().io + SupervisorJob()),
        ) { get<Context>().filesDir.resolve("puzzlebox.preferences_pb") }
    }
    single<UserPreferencesRepository> { DataStoreUserPreferencesRepository(get()) }
    // Debug-only prototype store (ticket 08) owns a separate file: a release build
    // never resolves this binding, so the file is never even created there. Koin
    // singles are lazy, which is what keeps that true without build-type modules.
    single {
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(get<DispatcherProvider>().io + SupervisorJob()),
        ) { get<Context>().filesDir.resolve("debug_style.preferences_pb") }
    }
    single<DebugStyleRepository> { DataStoreDebugStyleRepository(get()) }
}
