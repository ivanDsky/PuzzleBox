package com.ivandsky.puzzlebox.create

import com.ivandsky.puzzlebox.designsystem.MotionTokens

/**
 * The length of Create's crossfades, the tab switch and the first cloud restore's fill-in alike: MotionTokens
 * medium, the existing short fade under [reduced] motion.
 */
internal fun createFadeMillis(reduced: Boolean): Int =
    if (reduced) MotionTokens.ReducedFadeDuration else MotionTokens.DurationMedium

/** The whole screen is the restoring card: nothing stored yet and the first cloud restore is still running (A34). */
internal fun CreateUiState.restoringScreen(): Boolean = restoring && isEmpty

/** The shown tab is the restoring card: it has nothing yet while the first cloud restore is still running (A34). */
internal fun CreateUiState.restoringTab(): Boolean = restoring && when (tab) {
    CreateUiState.Tab.Published -> published.isEmpty()
    CreateUiState.Tab.Drafts -> draftCount == 0
}
