package com.ivandsky.puzzlebox.profile.di

import androidx.annotation.StringRes
import com.ivandsky.puzzlebox.app.auth.CredentialManagerGoogleIdTokenProvider
import com.ivandsky.puzzlebox.app.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.app.screens.ProfileViewModel
import com.ivandsky.puzzlebox.data.identity.IdentityService
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Profile feature wiring: the Credential Manager sign-in seam and ProfileRoute's
 * koinViewModel(). Today's board is the featured game's (domainModule's
 * ObserveTodayLeaderboard). [serverClientIdRes] is the app's google-services
 * `default_web_client_id` — that resource is generated into the app module only.
 */
fun profileModule(@StringRes serverClientIdRes: Int): Module = module {
    single<GoogleIdTokenProvider> {
        CredentialManagerGoogleIdTokenProvider(androidContext(), serverClientIdRes)
    }
    viewModel {
        ProfileViewModel(
            identityService = get<IdentityService>(),
            idTokenProvider = get<GoogleIdTokenProvider>(),
            observeTodayLeaderboard = get(),
        )
    }
}
