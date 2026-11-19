package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether [ArchiveMonthCalendar]'s grid swipes between months (backlog #1668): [LibraryRoute] provides it from
 * [LibraryViewModel.monthSwipe], and a preview or an isolated test gets the crossfade.
 */
internal val LocalArchiveMonthSwipe = staticCompositionLocalOf { false }
