package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.create.CreateViewModel
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.data.repository.SharedTextStore
import com.ivandsky.puzzlebox.domain.CreateDraft
import com.ivandsky.puzzlebox.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.domain.SyncSolvesCounts
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.repository.FakeCustomBoardRepository
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedTextStore
import java.time.Instant
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel

private val IdleNow: Instant = Instant.parse("2026-11-08T10:00:00Z")

/**
 * Registers the activity-scoped Create root view model the Create entries reach (the delete
 * Undo and the import notice ride on it), over [drafts] and no games. For Studio tests that
 * never list drafts: it holds nothing they look at.
 */
internal fun Module.idleCreateRoot(
    drafts: DraftRepository,
    sharedText: SharedTextStore = FakeSharedTextStore(),
) {
    viewModel {
        val games = GameRegistry(emptyList())
        CreateViewModel(
            games,
            GameEditorRegistry(emptyList()),
            GamePlayRegistry(emptyList()),
            drafts,
            FakePublishedPuzzleRepository(),
            CreateDraft(games, drafts, newId = { "new-1" }, clock = { IdleNow }),
            CreateDraftCopy(drafts, newId = { "copy-1" }, clock = { IdleNow }),
            DefaultDispatcherProvider(),
            sharedText = sharedText,
            syncSolves = SyncSolvesCounts(FakePublishedPuzzleRepository(), FakeCustomBoardRepository(), clock = { IdleNow }),
            releaseFlags = FakeReleaseFlags(),
            clock = { IdleNow },
        )
    }
}
