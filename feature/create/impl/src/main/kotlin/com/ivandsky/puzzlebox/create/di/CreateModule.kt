package com.ivandsky.puzzlebox.create.di

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.create.CreateViewModel
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.api.PublishedPuzzleKey
import com.ivandsky.puzzlebox.create.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.create.domain.CreateSharedPuzzle
import com.ivandsky.puzzlebox.create.domain.ImportDraftFromText
import com.ivandsky.puzzlebox.create.domain.SyncSolvesCounts
import com.ivandsky.puzzlebox.create.domain.UpdatePublishState
import com.ivandsky.puzzlebox.create.domain.ValidatePublish
import com.ivandsky.puzzlebox.create.editor.EditorViewModel
import com.ivandsky.puzzlebox.create.editor.FinishLevelChoice
import com.ivandsky.puzzlebox.create.published.PublishedPuzzleViewModel
import com.ivandsky.puzzlebox.gameui.flags.GameUiFlag
import java.time.Instant
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose

private val DraftSaves = named("create.draftSaves")

/**
 * Create feature wiring: [com.ivandsky.puzzlebox.domain.CreateDraft] comes from :core:domain's
 * domainModule; the [com.ivandsky.puzzlebox.game.GameRegistry], the
 * [com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry] and the
 * [com.ivandsky.puzzlebox.gameui.GamePlayRegistry] from the app's aggregation (ADR-0001).
 * Publishing ([CreateSharedPuzzle]) and its check ([ValidatePublish]), unpublishing
 * ([UpdatePublishState]), the Published rows' solve counts ([SyncSolvesCounts]), a draft's copy
 * ([CreateDraftCopy], a random UUID and the wall clock) and the text import
 * ([ImportDraftFromText]) are bound here, their only consumer. The
 * [com.ivandsky.puzzlebox.data.cloud.CloudSync], which says whether the lists are still being restored, comes from the data graph.
 * Each Studio asks [GameUiFlag.ModeTiles] as it opens, so a debug switch shows on the next open.
 */
val createModule: Module = module {
    // Process lifetime on main, like Play's slot writes: leaving the Studio never cancels its last save.
    single(DraftSaves) { CoroutineScope(SupervisorJob() + get<DispatcherProvider>().main) } onClose { it?.cancel() }
    viewModel { CreateViewModel(get(), get(), get(), get(), get(), get(), get(), get(), syncSolves = get(), sharedText = get(), clock = { Instant.now() }, cloudSync = get()) }
    // The Finish level the author last chose (90): one for the process, so every Studio opens its picker on it.
    single { FinishLevelChoice() }
    factory { SyncSolvesCounts(published = get(), boards = get(), clock = { Instant.now() }) }
    factory {
        CreateSharedPuzzle(
            drafts = get(),
            shared = get(),
            published = get(),
            identity = get(),
            validate = get(),
            random = Random.Default,
            clock = { Instant.now() },
        )
    }
    factory { UpdatePublishState(shared = get(), published = get()) }
    factory { CreateDraftCopy(drafts = get(), newId = { UUID.randomUUID().toString() }, clock = { Instant.now() }) }
    factory { ImportDraftFromText(registry = get(), createDraft = get(), drafts = get(), dispatchers = get()) }
    factory { ValidatePublish() }
    viewModel { (key: EditorKey) ->
        EditorViewModel(
            key, get(), get(), get(), get(), get(), get(),
            saves = get(DraftSaves),
            clock = { Instant.now() },
            createShared = get(),
            publishedPuzzles = get(),
            identity = get(),
            idTokens = get(),
            updatePublishState = get(),
            preferences = get(),
            validatePublish = get(),
            finishLevel = get(),
            modeTiles = get<ReleaseFlags>().isEnabled(GameUiFlag.ModeTiles),
        )
    }
    viewModel { (key: PublishedPuzzleKey) ->
        PublishedPuzzleViewModel(
            key = key,
            published = get(),
            drafts = get(),
            games = get(),
            editors = get(),
            plays = get(),
            copyDraft = get(),
            dispatchers = get(),
            updatePublishState = get(),
        )
    }
}
