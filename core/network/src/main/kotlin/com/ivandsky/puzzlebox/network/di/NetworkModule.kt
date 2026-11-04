package com.ivandsky.puzzlebox.network.di

import com.ivandsky.puzzlebox.game.FeedTransport
import com.ivandsky.puzzlebox.network.LINKEDIN_BASE_URL
import com.ivandsky.puzzlebox.network.LinkedInFeedTransport
import com.ivandsky.puzzlebox.network.LinkedInGamesApi
import com.ivandsky.puzzlebox.network.LinkedInSessionCookieJar
import com.ivandsky.puzzlebox.network.linkedInClient
import com.ivandsky.puzzlebox.network.retrofit
import kotlinx.serialization.json.Json
import org.koin.dsl.module

/**
 * Anonymous LinkedIn session (A7): per-process cookie jar + csrf echo on the one
 * LinkedIn client; every game's DailySource fetches through the one [FeedTransport].
 */
val networkModule = module {
    single { Json { ignoreUnknownKeys = true } }
    single { LinkedInSessionCookieJar() }
    single {
        retrofit(baseUrl = LINKEDIN_BASE_URL, client = linkedInClient(jar = get()), json = get())
            .create(LinkedInGamesApi::class.java)
    }
    single<FeedTransport> { LinkedInFeedTransport(get()) }
}
