package com.ivandsky.puzzlebox.data.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.db.Migrations
import com.ivandsky.puzzlebox.data.db.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.data.network.AuthHeaderSource
import com.ivandsky.puzzlebox.data.network.LinkedInSessionCookieJar
import com.ivandsky.puzzlebox.data.network.LinkedInZipApi
import com.ivandsky.puzzlebox.data.network.LinkedInZipClient
import com.ivandsky.puzzlebox.data.network.LINKEDIN_BASE_URL
import com.ivandsky.puzzlebox.data.network.PuzzleFeedApi
import com.ivandsky.puzzlebox.data.network.linkedInClient
import com.ivandsky.puzzlebox.data.network.okHttpClient
import com.ivandsky.puzzlebox.data.network.retrofit
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.DataStoreUserPreferencesRepository
import com.ivandsky.puzzlebox.data.repository.LinkedInZipDailySource
import com.ivandsky.puzzlebox.data.repository.OfflineFirstDailyPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.OfflineFirstZipDailyRepository
import com.ivandsky.puzzlebox.data.repository.RoomCompletionRepository
import com.ivandsky.puzzlebox.data.repository.ZipDailyRepository
import com.ivandsky.puzzlebox.data.repository.ZipDailySource
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.game.PuzzleSource
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
    // Anonymous LinkedIn session (A7): per-process cookie jar + csrf echo; the
    // AuthHeaderSource seam stays unused until a signed-in phase needs it. The client
    // stays private to the LinkedIn Retrofit (the exemplar OkHttpClient binding is untouched).
    single { LinkedInSessionCookieJar() }
    single {
        retrofit(baseUrl = LINKEDIN_BASE_URL, client = linkedInClient(jar = get()), json = get())
            .create(LinkedInZipApi::class.java)
    }
    single { LinkedInZipClient(get()) }
    single { LinkedInZipDailySource(get()) }
    single<ZipDailySource> { get<LinkedInZipDailySource>() }
    single<PuzzleSource> { get<LinkedInZipDailySource>() }
    single<ZipDailyRepository> { OfflineFirstZipDailyRepository(get(), get(), get()) }
    single {
        Room.databaseBuilder(get<Context>(), PuzzleBoxDatabase::class.java, PuzzleBoxDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .build()
    }
    single { get<PuzzleBoxDatabase>().dailyPuzzleDao() }
    single { get<PuzzleBoxDatabase>().puzzleDailyDao() }
    single { get<PuzzleBoxDatabase>().completionDao() }
    // Records/replays store (ADR-0004): the A4 first-win record is enforced here, so
    // feature modules consume the interface only (pinned ticket-15 contract).
    single<CompletionRepository> { RoomCompletionRepository(get(), get()) }
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
}
