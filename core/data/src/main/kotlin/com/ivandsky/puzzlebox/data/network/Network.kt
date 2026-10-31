package com.ivandsky.puzzlebox.data.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Placeholder base URL; Retrofit requires one at build time. Phase 1 finalizes the real hosts. */
const val LINKEDIN_BASE_URL = "https://www.linkedin.com/"

fun okHttpClient(authSource: AuthHeaderSource): OkHttpClient =
    OkHttpClient.Builder()
        .addInterceptor(AuthHeaderInterceptor(authSource))
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
                // WHY: session headers must never reach logcat.
                redactHeader("Authorization")
                redactHeader("Cookie")
            },
        )
        .build()

fun retrofit(baseUrl: String, client: OkHttpClient, json: Json): Retrofit =
    Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
