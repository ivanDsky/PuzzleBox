package com.ivandsky.puzzlebox.create

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/**
 * What the Custom list does over the one [viewModel] while it shows: each draft it stores, a new one or an imported
 * one, is reported once to [onOpen]; and while the list is resumed, the text another app shared is imported
 * ([CreateViewModel.importSharedText]). Only one Custom list may show at a time: a stored draft's id goes to a single
 * collector. A null [onOpen] leaves the reporting to a [NewPuzzleHost], which then is that collector.
 */
@Composable
internal fun CreateEffects(viewModel: CreateViewModel, onOpen: ((draftId: String) -> Unit)?) {
    val open by rememberUpdatedState(onOpen)
    if (open != null) LaunchedEffect(viewModel) { viewModel.created.collect { open?.invoke(it) } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.importSharedText() }
    }
}
