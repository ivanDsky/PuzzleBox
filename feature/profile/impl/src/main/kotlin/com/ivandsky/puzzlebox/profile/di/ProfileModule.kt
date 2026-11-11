package com.ivandsky.puzzlebox.profile.di

import android.webkit.CookieManager
import android.webkit.WebSettings
import com.ivandsky.puzzlebox.app.screens.ProfileViewModel
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSessionCookies
import com.ivandsky.puzzlebox.profile.linkedin.WebViewLinkedInSession
import com.ivandsky.puzzlebox.profile.settings.SettingsViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Profile feature wiring: ProfileRoute's koinViewModel(). The sign-in seam comes from
 * authModule, the use cases from domainModule; the GameRegistry, the per-game brands
 * (GamePlayRegistry) and UserPreferencesRepository come from the app graph; the
 * SavedStateHandle (the board pick, first in the repo) is injected by Koin's viewModel scope.
 */
val profileModule: Module = module {
    viewModel {
        ProfileViewModel(
            identityService = get<IdentityService>(),
            idTokenProvider = get<GoogleIdTokenProvider>(),
            observeLeaderboard = get(),
            getFeaturedGame = get(),
            observeGameStats = get(),
            observeGameHistory = get(),
            registry = get(),
            brands = get(),
            preferences = get(),
            observeLatestSolvedToday = get(),
            savedStateHandle = get(),
        )
    }
    viewModel { SettingsViewModel(preferences = get(), appBuild = get()) }
    // Both providers are lambdas, so resolving this neither starts the web view provider nor reads a cookie.
    single<LinkedInSessionCookies> { WebViewLinkedInSession({ CookieManager.getInstance() }, { WebSettings.getDefaultUserAgent(get()) }, get()) }
}
