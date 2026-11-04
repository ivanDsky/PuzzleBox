package com.ivandsky.puzzlebox.data.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.db.Migrations
import com.ivandsky.puzzlebox.data.db.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.data.network.LINKEDIN_BASE_URL
import com.ivandsky.puzzlebox.data.network.LinkedInFeedTransport
import com.ivandsky.puzzlebox.data.network.LinkedInGamesApi
import com.ivandsky.puzzlebox.data.network.LinkedInSessionCookieJar
import com.ivandsky.puzzlebox.data.network.linkedInClient
import com.ivandsky.puzzlebox.data.network.retrofit
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.DataStoreUserPreferencesRepository
import com.ivandsky.puzzlebox.data.repository.OfflineFirstDailyRepository
import com.ivandsky.puzzlebox.data.repository.RoomCompletionRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.game.FeedTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.koin.dsl.module

/**
 * Game-agnostic data wiring. Games plug in through Koin, never through this module:
 * every game module binds its `DailySource` (collected here by `getAll`), and `app`
 * binds the `GameRegistry` the repositories decode through.
 */
val dataModule = module {
    single { Json { ignoreUnknownKeys = true } }
    // Anonymous LinkedIn session (A7): per-process cookie jar + csrf echo on the one
    // LinkedIn client; every game's DailySource fetches through the one FeedTransport.
    single { LinkedInSessionCookieJar() }
    single {
        retrofit(baseUrl = LINKEDIN_BASE_URL, client = linkedInClient(jar = get()), json = get())
            .create(LinkedInGamesApi::class.java)
    }
    single<FeedTransport> { LinkedInFeedTransport(get()) }
    single<DailyRepository> {
        OfflineFirstDailyRepository(
            sources = getAll<DailySource>().associateBy { it.gameId },
            registry = get(),
            dao = get(),
            dispatchers = get(),
        )
    }
    single {
        Room.databaseBuilder(get<Context>(), PuzzleBoxDatabase::class.java, PuzzleBoxDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .build()
    }
    single { get<PuzzleBoxDatabase>().puzzleDailyDao() }
    single { get<PuzzleBoxDatabase>().completionDao() }
    // Records/replays store (ADR-0004): the A4 first-win record is enforced here, so
    // feature modules consume the interface only (pinned ticket-15 contract).
    single<CompletionRepository> { RoomCompletionRepository(get(), get(), get()) }
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
