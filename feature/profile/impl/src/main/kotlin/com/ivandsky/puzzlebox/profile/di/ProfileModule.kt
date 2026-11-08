package com.ivandsky.puzzlebox.profile.di

import com.ivandsky.puzzlebox.app.screens.ProfileViewModel
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.identity.IdentityService
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Profile feature wiring: ProfileRoute's koinViewModel(). The sign-in seam comes from
 * authModule, the use cases from domainModule; the GameRegistry, the per-game brands
 * (GamePlayRegistry) and UserPreferencesRepository from the app graph.
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
        )
    }
}
