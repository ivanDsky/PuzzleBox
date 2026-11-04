package com.ivandsky.puzzlebox.data.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.db.Migrations
import com.ivandsky.puzzlebox.data.db.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.data.network.LinkedInSessionCookieJar
import com.ivandsky.puzzlebox.data.network.LinkedInQueensApi
import com.ivandsky.puzzlebox.data.network.LinkedInQueensClient
import com.ivandsky.puzzlebox.data.network.LinkedInTangoApi
import com.ivandsky.puzzlebox.data.network.LinkedInTangoClient
import com.ivandsky.puzzlebox.data.network.LinkedInZipApi
import com.ivandsky.puzzlebox.data.network.LinkedInZipClient
import com.ivandsky.puzzlebox.data.network.LINKEDIN_BASE_URL
import com.ivandsky.puzzlebox.data.network.linkedInClient
import com.ivandsky.puzzlebox.data.network.retrofit
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DataStoreUserPreferencesRepository
import com.ivandsky.puzzlebox.data.repository.LinkedInTangoDailySource
import com.ivandsky.puzzlebox.data.repository.TangoSeedBackedSource
import com.ivandsky.puzzlebox.data.repository.LinkedInZipDailySource
import com.ivandsky.puzzlebox.data.repository.OfflineFirstTangoDailyRepository
import com.ivandsky.puzzlebox.data.repository.OfflineFirstZipDailyRepository
import com.ivandsky.puzzlebox.data.repository.RoomCompletionRepository
import com.ivandsky.puzzlebox.data.repository.TangoDailyRepository
import com.ivandsky.puzzlebox.data.repository.TangoDailySource
import com.ivandsky.puzzlebox.data.repository.ZipDailyRepository
import com.ivandsky.puzzlebox.data.repository.ZipDailySource
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.data.repository.QueensDailyRepository
import com.ivandsky.puzzlebox.data.repository.OfflineFirstQueensDailyRepository
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.feed.LinkedInQueensDailySource
import com.ivandsky.puzzlebox.games.queens.feed.QueensDailySource
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.game.PuzzleSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module

val dataModule = module {
    single { Json { ignoreUnknownKeys = true } }
    // Anonymous LinkedIn session (A7): per-process cookie jar + csrf echo; each LinkedIn
    // Retrofit builds its own client from it.
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
    // Queens daily feed (queens-06): same LinkedIn session seams (cookie jar + csrf echo),
    // the GraphQL/stream ladder, the A13-gated source, and the offline-first cache.
    single {
        retrofit(baseUrl = LINKEDIN_BASE_URL, client = linkedInClient(jar = get()), json = get())
            .create(LinkedInQueensApi::class.java)
    }
    single { LinkedInQueensClient(get()) }
    // The source consumes the BodyClient seam (fun interface), so the Retrofit client
    // binds to it explicitly — the queens-06 registration line never resolved without
    // this binding (exposed by queens-10's DI-graph test).
    single<LinkedInQueensDailySource.BodyClient> {
        val client = get<LinkedInQueensClient>()
        LinkedInQueensDailySource.BodyClient { client.fetchDailyBody() }
    }
    single { LinkedInQueensDailySource(get()) }
    single<QueensDailySource> { get<LinkedInQueensDailySource>() }
    single<PuzzleSource>(qualifier = qualifier(QueensDefinition.id.value)) { get<LinkedInQueensDailySource>() }
    single<QueensDailyRepository> { OfflineFirstQueensDailyRepository(get(), get(), get()) }
    // Tango daily fetch (ticket 03): same LinkedIn ladder, Tango route; the PuzzleSource
    // binding carries the game-id qualifier so the archive seam can resolve per game.
    single {
        retrofit(baseUrl = LINKEDIN_BASE_URL, client = linkedInClient(jar = get()), json = get())
            .create(LinkedInTangoApi::class.java)
    }
    single { LinkedInTangoClient(get()) }
    single { LinkedInTangoDailySource(get()) }
    single<TangoDailySource> { TangoSeedBackedSource(get<LinkedInTangoDailySource>()) }
    single<PuzzleSource>(qualifier = qualifier(TangoDefinition.id.value)) { TangoSeedBackedSource(get<LinkedInTangoDailySource>()) }
    single<TangoDailyRepository> { OfflineFirstTangoDailyRepository(get(), get(), get()) }
    single {
        Room.databaseBuilder(get<Context>(), PuzzleBoxDatabase::class.java, PuzzleBoxDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .build()
    }
    single { get<PuzzleBoxDatabase>().puzzleDailyDao() }
    single { get<PuzzleBoxDatabase>().completionDao() }
    // Records/replays store (ADR-0004): the A4 first-win record is enforced here, so
    // feature modules consume the interface only (pinned ticket-15 contract).
    single<CompletionRepository> { RoomCompletionRepository(get(), get()) }
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
