package com.ivandsky.puzzlebox.backend.firebase

import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.repository.CustomBoardRepository
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import kotlinx.coroutines.CoroutineScope
import org.koin.dsl.module

/**
 * Backend bindings (design §1): the contract seams bind to this module's
 * implementations — swapping backends later means changing these bindings and
 * deleting the module, never touching `core/` or `:feature:*`.
 *
 * Wiring contract: a [CoroutineScope] single must exist in the graph (the app module
 * provides the process-long application scope the identity StateFlow shares on).
 */
val firebaseBackendModule = module {
    single<AuthGateway> { FirebaseAuthGateway() }
    single<IdentityService> { FirebaseIdentityService(gateway = get(), scope = get()) }
    single<LeaderboardStore> { FirestoreLeaderboardStore() }
    single<LeaderboardRepository> {
        FirestoreLeaderboardRepository(
            store = get(),
            currentUid = { get<IdentityService>().current.value?.uid },
        )
    }
    single<SharedPuzzleStore> { FirestoreSharedPuzzleStore() }
    single<SharedPuzzleRepository> {
        FirestoreSharedPuzzleRepository(
            store = get(),
            currentUid = { get<IdentityService>().current.value?.uid },
        )
    }
    single<CustomBoardStore> { FirestoreCustomBoardStore() }
    single<CustomBoardRepository> {
        FirestoreCustomBoardRepository(
            store = get(),
            currentUid = { get<IdentityService>().current.value?.uid },
        )
    }
}
