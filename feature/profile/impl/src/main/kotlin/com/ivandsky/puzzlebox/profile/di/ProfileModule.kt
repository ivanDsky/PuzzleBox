package com.ivandsky.puzzlebox.profile.di

import android.webkit.CookieManager
import android.webkit.WebSettings
import com.ivandsky.puzzlebox.app.screens.ProfileViewModel
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.cloud.CloudSync
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSessionCookies
import com.ivandsky.puzzlebox.profile.linkedin.LinkedInLoginViewModel
import com.ivandsky.puzzlebox.profile.linkedin.WebViewLinkedInSession
import com.ivandsky.puzzlebox.profile.settings.SettingsViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Profile feature wiring: ProfileRoute's koinViewModel(). The sign-in seam comes from
 * authModule, the use cases from domainModule.
 */
val profileModule: Module = module {
    // The parameter says whether the LinkedIn row is on; off, no LinkedIn object is resolved at all.
    viewModel { params ->
        val linkedIn = params.getOrNull<Boolean>() == true
        ProfileViewModel(
            identityService = get<IdentityService>(),
            idTokenProvider = get<GoogleIdTokenProvider>(),
            observeGameStats = get(),
            link = if (linkedIn) get() else null,
            observeAccount = if (linkedIn) get() else null,
            // Bound only with the CloudSync switch on; unbound is today's Google row.
            cloudSync = getOrNull<CloudSync>(),
        )
    }
    viewModel { SettingsViewModel(preferences = get(), games = get(), appBuild = get()) }
    viewModel { LinkedInLoginViewModel(createLink = get(), cookies = get()) }
    // Both providers are lambdas, so resolving this neither starts the web view provider nor reads a cookie.
    single<LinkedInSessionCookies> { WebViewLinkedInSession({ CookieManager.getInstance() }, { WebSettings.getDefaultUserAgent(get()) }, get()) }
}
