package com.ivandsky.puzzlebox.network.di

import com.ivandsky.puzzlebox.game.FeedTransport
import com.ivandsky.puzzlebox.game.NytTransport
import com.ivandsky.puzzlebox.network.GITHUB_API_BASE_URL
import com.ivandsky.puzzlebox.network.GitHubReleaseTransport
import com.ivandsky.puzzlebox.network.LINKEDIN_BASE_URL
import com.ivandsky.puzzlebox.network.NYT_BASE_URL
import com.ivandsky.puzzlebox.network.NytApi
import com.ivandsky.puzzlebox.network.NytHttpTransport
import com.ivandsky.puzzlebox.network.gitHubReleaseTransport
import com.ivandsky.puzzlebox.network.linkedInFeedTransport
import com.ivandsky.puzzlebox.network.nytClient
import com.ivandsky.puzzlebox.network.retrofit
import kotlinx.serialization.json.Json
import org.koin.dsl.module

/**
 * Anonymous LinkedIn session (A7): per-process cookie jar + csrf echo on the one
 * LinkedIn client; every game's DailySource fetches through the one [FeedTransport].
 * NYT games (ADR-0014) ride their own sessionless client under [NytTransport]; the update
 * check (A2) rides another under [GitHubReleaseTransport].
 */
val networkModule = module {
    single { Json { ignoreUnknownKeys = true } }
    single<FeedTransport> { linkedInFeedTransport(LINKEDIN_BASE_URL, get()) }
    single {
        retrofit(baseUrl = NYT_BASE_URL, client = nytClient(), json = get()).create(NytApi::class.java)
    }
    single<NytTransport> { NytHttpTransport(get()) }
    single<GitHubReleaseTransport> { gitHubReleaseTransport(GITHUB_API_BASE_URL, get()) }
}
