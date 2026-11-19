package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.staticCompositionLocalOf

/** How the month calendar's grid changes when ‹ ›, a jump or "Today" pages it (backlog #1551). */
internal enum class ArchiveMonthTransition {
    /** The new grid dissolves in over the old one, in place. */
    Crossfade,

    /** The same dissolve, and both grids travel a twelfth of the width toward the side the month was paged to. */
    Slide,
}

/**
 * The transition [ArchiveMonthCalendar] uses: [LibraryRoute] provides it from [LibraryViewModel.monthTransition], and a
 * preview or an isolated test gets the crossfade.
 */
internal val LocalArchiveMonthTransition = staticCompositionLocalOf { ArchiveMonthTransition.Crossfade }
