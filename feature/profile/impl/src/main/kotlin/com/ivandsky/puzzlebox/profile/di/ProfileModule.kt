package com.ivandsky.puzzlebox.profile.di

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import com.ivandsky.puzzlebox.app.screens.ProfileViewModel
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.data.cloud.CloudSync
import com.ivandsky.puzzlebox.data.cloud.OversizedDrafts
import com.ivandsky.puzzlebox.data.cloud.SavedBoardsCloud
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSessionCookies
import com.ivandsky.puzzlebox.profile.linkedin.LinkedInLoginViewModel
import com.ivandsky.puzzlebox.profile.linkedin.WebViewLinkedInSession
import com.ivandsky.puzzlebox.profile.settings.RemindersViewModel
import com.ivandsky.puzzlebox.profile.settings.SettingsViewModel
import com.ivandsky.puzzlebox.profile.whatsnew.AssetWhatsNewSource
import com.ivandsky.puzzlebox.profile.whatsnew.WhatsNewSource
import com.ivandsky.puzzlebox.profile.whatsnew.WhatsNewViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
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
            updatePosting = if (linkedIn) get() else null,
            cloudSync = get(),
            oversizedDrafts = get(),
            savedBoardsCloud = get(),
        )
    }
    // The parameter is the app's VisibilitySlot; without it Settings has no Visibility section. The WhatsNew flag is read
    // here (ADR-0022 §3) and Settings gets one Boolean.
    viewModel { params ->
        SettingsViewModel(
            preferences = get(),
            games = get(),
            appBuild = get(),
            identityService = get(),
            idTokenProvider = get(),
            visibility = params.getOrNull(),
            whatsNew = getOrNull<ReleaseFlags>()?.isEnabled(ReleaseFlag.WhatsNew) == true,
        )
    }
    // The Reminders flag is read here (ADR-0022 §3); off, the block draws nothing and the view model reads nothing.
    viewModel {
        RemindersViewModel(
            enabled = getOrNull<ReleaseFlags>()?.isEnabled(ReleaseFlag.Reminders) == true,
            repository = get(),
            access = get(),
        )
    }
    single<WhatsNewSource> { AssetWhatsNewSource(get<Context>()) }
    viewModel { WhatsNewViewModel(source = get(), dispatchers = get()) }
    viewModel { LinkedInLoginViewModel(createLink = get(), cookies = get()) }
    // Both providers are lambdas, so resolving this neither starts the web view provider nor reads a cookie.
    single<LinkedInSessionCookies> { WebViewLinkedInSession({ CookieManager.getInstance() }, { WebSettings.getDefaultUserAgent(get()) }, get()) }
}
