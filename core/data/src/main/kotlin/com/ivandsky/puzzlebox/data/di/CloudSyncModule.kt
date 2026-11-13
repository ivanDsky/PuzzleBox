package com.ivandsky.puzzlebox.data.di

import com.ivandsky.puzzlebox.data.cloud.CloudErase
import com.ivandsky.puzzlebox.data.cloud.CloudSync
import com.ivandsky.puzzlebox.data.cloud.CloudSyncRunner
import com.ivandsky.puzzlebox.data.cloud.DataStoreSyncStateStore
import com.ivandsky.puzzlebox.data.cloud.OwnerScope
import com.ivandsky.puzzlebox.data.cloud.SignedInOwnerScope
import com.ivandsky.puzzlebox.data.cloud.SyncStateStore
import com.ivandsky.puzzlebox.data.cloud.SyncedKind
import com.ivandsky.puzzlebox.data.cloud.SyncedKindsCloudSync
import com.ivandsky.puzzlebox.data.cloud.kinds.CompletionsKind
import com.ivandsky.puzzlebox.data.cloud.kinds.DraftsKind
import com.ivandsky.puzzlebox.data.cloud.kinds.PublishedKind
import com.ivandsky.puzzlebox.data.cloud.kinds.ReceivedKind
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.network.NetworkStatus
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The cloud sync graph (ADR-0029 decision 5): loaded only when `ReleaseFlag.CloudSync` is on, decided once at app
 * start, and loaded with overrides allowed because it replaces [dataModule]'s no-op [OwnerScope]. With the flag off none
 * of it exists: `getOrNull<CloudSync>()` is null, no kind is registered and no cloud call can be made. The app supplies
 * the `CloudSpace` (the backend module), the `IdentityService`, the `NetworkStatus` and the process `CoroutineScope`
 * (a SupervisorJob scope the engine, the runner and the owner scope launch on).
 *
 * Every kind is a `SyncedKind` under its own qualifier: Koin keys a definition by type and qualifier, so unqualified
 * lines would replace one another, and `getAll<SyncedKind>()` still collects them all. `linkedInCloudSyncModule` adds
 * the boards kind to the same list, so the engine, the erase and the runner always see one list.
 */
val cloudSyncModule = module {
    single<SyncStateStore> { DataStoreSyncStateStore(get()) }
    // Collects from construction and is the only writer of the last uid, so it is created at start, not at first use.
    single<OwnerScope>(createdAtStart = true) { SignedInOwnerScope(get<IdentityService>(), get(), get<CoroutineScope>()) }
    single {
        CloudErase(space = get(), kinds = getAll<SyncedKind>(), store = get(), identity = get<IdentityService>())
    }
    single<CloudSync> {
        SyncedKindsCloudSync(
            kinds = getAll<SyncedKind>(),
            space = get(),
            store = get(),
            identity = get<IdentityService>(),
            erase = get(),
            clock = Clock.systemDefaultZone(),
            dispatchers = get(),
            scope = get<CoroutineScope>(),
        )
    }
    single {
        CloudSyncRunner(
            engine = get<CloudSync>(),
            kinds = getAll<SyncedKind>(),
            identity = get<IdentityService>(),
            network = get<NetworkStatus>(),
            scope = get<CoroutineScope>(),
        )
    }
    single<SyncedKind>(named("cloud.kind.completions")) {
        val db = get<PuzzleBoxDatabase>()
        CompletionsKind(db, db.completionDao())
    }
    single<SyncedKind>(named("cloud.kind.drafts")) {
        val db = get<PuzzleBoxDatabase>()
        DraftsKind(db, db.draftDao(), db.syncTombstoneDao(), Clock.systemDefaultZone())
    }
    single<SyncedKind>(named("cloud.kind.published")) {
        val db = get<PuzzleBoxDatabase>()
        PublishedKind(db, db.publishedPuzzleDao())
    }
    single<SyncedKind>(named("cloud.kind.received")) {
        val db = get<PuzzleBoxDatabase>()
        ReceivedKind(db, db.receivedPuzzleDao(), db.syncTombstoneDao(), Clock.systemDefaultZone())
    }
}
