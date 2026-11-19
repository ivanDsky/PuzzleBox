package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether [GamePage] ends How to play with "Make your own <Game>" instead of the full-width Make one (backlog #1567):
 * [LibraryRoute] provides it from [LibraryViewModel.makeOwnLink], and a preview or an isolated test gets the button.
 */
internal val LocalMakeOwnLink = staticCompositionLocalOf { false }
