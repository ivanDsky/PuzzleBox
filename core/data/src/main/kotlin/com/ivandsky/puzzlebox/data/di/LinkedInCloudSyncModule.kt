package com.ivandsky.puzzlebox.data.di

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.cloud.CloudSpace
import com.ivandsky.puzzlebox.data.cloud.OwnerScope
import com.ivandsky.puzzlebox.data.cloud.SavedBoardsCloud
import com.ivandsky.puzzlebox.data.cloud.SyncedKind
import com.ivandsky.puzzlebox.data.cloud.kinds.BoardsKind
import com.ivandsky.puzzlebox.data.linkedin.LinkedInLink
import com.ivandsky.puzzlebox.data.linkedin.LinkedInStoreLock
import com.ivandsky.puzzlebox.data.linkedin.RoomSavedBoardsCloud
import com.ivandsky.puzzlebox.database.linkedin.LinkedInDatabase
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The LinkedIn side of cloud sync: loaded after [cloudSyncModule] by the app, and only when `ReleaseFlag.LinkedInAccount`
 * is on. It adds the boards kind to the `SyncedKind` list and binds the saved-board deletion the LinkedIn row resolves
 * with `getOrNull<SavedBoardsCloud>()`. Both take the one [LinkedInStoreLock] single `dataModule` binds, the lock the
 * day sync and the disconnect wipe already hold, so a cloud pass never interleaves with them.
 */
val linkedInCloudSyncModule = module {
    single<SyncedKind>(named("cloud.kind.boards")) {
        BoardsKind(get<LinkedInDatabase>(), get<LinkedInStoreLock>(), get<LinkedInLink>(), get<GameCalendar>())
    }
    single<SavedBoardsCloud> {
        RoomSavedBoardsCloud(get<LinkedInDatabase>(), get<LinkedInStoreLock>(), get<CloudSpace>(), get<OwnerScope>())
    }
}
