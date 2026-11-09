package com.ivandsky.puzzlebox.data.di

import com.ivandsky.puzzlebox.data.calendar.CadenceGameCalendar
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.match.MatchLocalRepository
import com.ivandsky.puzzlebox.data.match.MatchResultOutbox
import com.ivandsky.puzzlebox.data.match.RoomMatchLocalRepository
import com.ivandsky.puzzlebox.data.match.RoomMatchResultOutbox
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.GameSettingsRepository
import com.ivandsky.puzzlebox.data.repository.InMemorySharedTextStore
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.data.repository.OfflineFirstDailyRepository
import com.ivandsky.puzzlebox.data.repository.OfflineGameSettingsRepository
import com.ivandsky.puzzlebox.data.repository.OfflineUserPreferencesRepository
import com.ivandsky.puzzlebox.data.repository.PublishedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.RoomCompletionRepository
import com.ivandsky.puzzlebox.data.repository.RoomDraftRepository
import com.ivandsky.puzzlebox.data.repository.RoomPublishedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.RoomReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.RoomSavedPlayRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.data.repository.SharedTextStore
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.data.update.AppReleaseRepository
import com.ivandsky.puzzlebox.data.update.NetworkAppReleaseRepository
import com.ivandsky.puzzlebox.database.di.databaseModule
import com.ivandsky.puzzlebox.datastore.di.datastoreModule
import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.network.di.networkModule
import org.koin.dsl.module

/**
 * Game-agnostic data wiring. Games plug in through Koin, never through this module:
 * every game module binds its `DailySource` (collected here by `getAll`), and `app`
 * binds the `GameRegistry` the repositories decode through. Room, the LinkedIn
 * transport and the preferences DataStore come from the included source modules.
 */
val dataModule = module {
    includes(databaseModule, networkModule, datastoreModule)
    // Per-game "today" (A9/A23): the daily fetch and the hub cues share one calendar.
    single<GameCalendar> { CadenceGameCalendar(get()) }
    single<DailyRepository> {
        OfflineFirstDailyRepository(
            sources = getAll<DailySource>().associateBy { it.gameId },
            registry = get(),
            dao = get(),
            dispatchers = get(),
            calendar = get(),
        )
    }
    // Records/replays store (ADR-0004): the A4 first-win record is enforced here, so
    // feature modules consume the interface only (pinned ticket-15 contract).
    single<CompletionRepository> { RoomCompletionRepository(get(), get(), get()) }
    single<UserPreferencesRepository> { OfflineUserPreferencesRepository(get()) }
    single<GameSettingsRepository> { OfflineGameSettingsRepository(get()) }
    // Saved in-progress plays (A10); the slot cap is private to the Room adapter.
    single<SavedPlayRepository> { RoomSavedPlayRepository(get(), get(), get()) }
    // Editor drafts (A5): features read and write through the port, never Room.
    single<DraftRepository> { RoomDraftRepository(get()) }
    // The author's published listing; the board stays on the kept, read-only draft.
    single<PublishedPuzzleRepository> { RoomPublishedPuzzleRepository(get()) }
    // Friends' puzzles opened here; they keep playing offline after their author unpublishes.
    single<ReceivedPuzzleRepository> { RoomReceivedPuzzleRepository(get()) }
    // This device's memory of duels (invite opened, reveal seen, dismissed).
    single<MatchLocalRepository> { RoomMatchLocalRepository(get()) }
    single<MatchResultOutbox> { RoomMatchResultOutbox(get()) }
    // Process-wide so the 15-minute update-check throttle survives screen changes.
    single<AppReleaseRepository> { NetworkAppReleaseRepository(get()) }
    // Shared-text hand-off (M5-08): app puts, the Create root takes it exactly once. In memory
    // only — the shared text is transient, never persisted, never in a NavKey (P8).
    single<SharedTextStore> { InMemorySharedTextStore() }
}
