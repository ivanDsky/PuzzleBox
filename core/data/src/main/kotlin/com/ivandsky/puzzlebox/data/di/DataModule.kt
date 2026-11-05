package com.ivandsky.puzzlebox.data.di

import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.DataStoreKeptSessionRepository
import com.ivandsky.puzzlebox.data.repository.KeptSessionRepository
import com.ivandsky.puzzlebox.data.repository.OfflineFirstDailyRepository
import com.ivandsky.puzzlebox.data.repository.OfflineUserPreferencesRepository
import com.ivandsky.puzzlebox.data.repository.RoomCompletionRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.database.di.databaseModule
import com.ivandsky.puzzlebox.datastore.di.datastoreModule
import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.network.di.networkModule
import org.koin.dsl.module

/**
 * Game-agnostic data wiring. Games plug in through Koin, never through this module:
 * every game module binds its `DailySource` (collected here by `getAll`), and `app`
 * binds the `GameRegistry` the repositories decode through. Room, the LinkedIn
 * transport and the preferences and kept-session DataStores come from the included source modules.
 */
val dataModule = module {
    includes(databaseModule, networkModule, datastoreModule)
    single<DailyRepository> {
        OfflineFirstDailyRepository(
            sources = getAll<DailySource>().associateBy { it.gameId },
            registry = get(),
            dao = get(),
            dispatchers = get(),
        )
    }
    // Records/replays store (ADR-0004): the A4 first-win record is enforced here, so
    // feature modules consume the interface only (pinned ticket-15 contract).
    single<CompletionRepository> { RoomCompletionRepository(get(), get(), get()) }
    single<UserPreferencesRepository> { OfflineUserPreferencesRepository(get()) }
    // Kept sessions (spec A3): Home and Play both read them, so they live behind core data.
    single<KeptSessionRepository> { DataStoreKeptSessionRepository(get()) }
}
