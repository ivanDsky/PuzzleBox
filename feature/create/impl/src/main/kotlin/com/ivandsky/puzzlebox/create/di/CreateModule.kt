package com.ivandsky.puzzlebox.create.di

import com.ivandsky.puzzlebox.create.CreateViewModel
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.editor.EditorViewModel
import java.time.Instant
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Create feature wiring: [com.ivandsky.puzzlebox.domain.CreateDraft] comes from :core:domain's
 * domainModule; the [com.ivandsky.puzzlebox.game.GameRegistry] and the
 * [com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry] from the app's aggregation (ADR-0001).
 */
val createModule: Module = module {
    viewModel { CreateViewModel(get(), get(), get(), get()) }
    viewModel { (key: EditorKey) -> EditorViewModel(key, get(), get(), get(), get(), clock = { Instant.now() }) }
}
