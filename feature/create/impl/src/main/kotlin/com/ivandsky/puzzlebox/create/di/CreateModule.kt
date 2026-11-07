package com.ivandsky.puzzlebox.create.di

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.create.CreateViewModel
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.editor.EditorViewModel
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

private val DraftSaves = named("create.draftSaves")

/**
 * Create feature wiring: [com.ivandsky.puzzlebox.domain.CreateDraft] comes from :core:domain's
 * domainModule; the [com.ivandsky.puzzlebox.game.GameRegistry], the
 * [com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry] and the
 * [com.ivandsky.puzzlebox.gameui.GamePlayRegistry] from the app's aggregation (ADR-0001).
 */
val createModule: Module = module {
    // Process lifetime on main, like Play's slot writes: leaving the Studio never cancels its last save.
    single(DraftSaves) { CoroutineScope(SupervisorJob() + get<DispatcherProvider>().main) }
    viewModel { CreateViewModel(get(), get(), get(), get(), get(), get(), get(), clock = { Instant.now() }) }
    viewModel { (key: EditorKey) ->
        EditorViewModel(key, get(), get(), get(), get(), get(), get(), saves = get(DraftSaves), clock = { Instant.now() }, validatePublish = get())
    }
}
