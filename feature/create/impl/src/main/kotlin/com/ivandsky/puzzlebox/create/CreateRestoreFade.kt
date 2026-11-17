package com.ivandsky.puzzlebox.create

import com.ivandsky.puzzlebox.designsystem.MotionTokens

/**
 * The length of the Custom list's first cloud restore fill-in crossfade: MotionTokens medium, the existing short fade
 * under [reduced] motion.
 */
internal fun createFadeMillis(reduced: Boolean): Int =
    if (reduced) MotionTokens.ReducedFadeDuration else MotionTokens.DurationMedium

/** The whole list is the restoring card: nothing stored yet and the first cloud restore is still running (A34). */
internal fun CreateUiState.restoringScreen(): Boolean = restoring && isEmpty
