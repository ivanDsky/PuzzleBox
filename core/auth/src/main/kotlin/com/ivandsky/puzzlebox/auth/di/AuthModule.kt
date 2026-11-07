package com.ivandsky.puzzlebox.auth.di

import androidx.annotation.StringRes
import com.ivandsky.puzzlebox.auth.CredentialManagerGoogleIdTokenProvider
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Google sign-in wiring. [serverClientIdRes] is the app's google-services
 * `default_web_client_id` — that resource is generated into the app module only.
 */
fun authModule(@StringRes serverClientIdRes: Int): Module = module {
    single<GoogleIdTokenProvider> {
        CredentialManagerGoogleIdTokenProvider(androidContext(), serverClientIdRes)
    }
}
