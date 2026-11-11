package com.ivandsky.puzzlebox.network.linkedin

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

internal fun buildAccountClient(): OkHttpClient =
    OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor())
        .build()
