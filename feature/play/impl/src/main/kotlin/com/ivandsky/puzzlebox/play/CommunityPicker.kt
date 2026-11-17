package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How a Community row opens its puzzle when the tab shows two panes (the tablet): it picks the puzzle for the right
 * pane instead of pushing a page of its own. [puzzle] is a puzzle opened by its share code, a New today row or a
 * friend's; [published] is one of your own published puzzles. The app's [CustomSlot] reads [LocalCommunityPicker] and
 * falls back to pushing the page when there is none (the phone, and Games' Custom pill).
 */
interface CommunityPicker {
    fun puzzle(code: String)
    fun published(code: String)
}

/** The picker of the Community two-pane around this composition, or null where a row opens a page of its own. */
val LocalCommunityPicker = staticCompositionLocalOf<CommunityPicker?> { null }
