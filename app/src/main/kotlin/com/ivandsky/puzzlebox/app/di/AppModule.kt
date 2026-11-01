package com.ivandsky.puzzlebox.app.di

import com.ivandsky.puzzlebox.app.auth.CredentialManagerGoogleIdTokenProvider
import com.ivandsky.puzzlebox.app.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.app.leaderboard.LeaderboardSubmitWatcher
import com.ivandsky.puzzlebox.app.screens.ProfileViewModel
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/** App-process wiring: the application scope and the platform seams. */
val appModule = module {
    // Process-long scope for fire-and-forget work (leaderboard submissions, the
    // identity StateFlow): SupervisorJob so one failed child never kills the scope,
    // default dispatcher from the provider — same posture as the DataStore scope.
    single<CoroutineScope> {
        CoroutineScope(SupervisorJob() + get<DispatcherProvider>().default)
    }
    single<GoogleIdTokenProvider> {
        CredentialManagerGoogleIdTokenProvider(androidContext())
    }
    // ProfileRoute's koinViewModel(). Today's board is the official Zip daily — the
    // same "zip" gameId the tests and firestore.rules encode; core:data has no game
    // registry to source it from yet, so the literal lives here, once.
    viewModel {
        ProfileViewModel(
            identityService = get<IdentityService>(),
            idTokenProvider = get<GoogleIdTokenProvider>(),
            leaderboard = get<LeaderboardRepository>(),
            todayBoard = { LeaderboardRef("zip", LocalDate.now()) },
        )
    }
    single {
        LeaderboardSubmitWatcher(
            records = get<CompletionRepository>(),
            identityService = get<IdentityService>(),
            leaderboard = get<LeaderboardRepository>(),
            scope = get<CoroutineScope>(),
        )
    }
}
