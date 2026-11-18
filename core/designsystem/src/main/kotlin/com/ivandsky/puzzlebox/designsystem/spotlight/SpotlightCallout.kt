package com.ivandsky.puzzlebox.designsystem.spotlight

import androidx.compose.runtime.Immutable

/**
 * Text drawn in the inverse-surface callout; [anchor] = cells it sits beside, never over a lit cell. [action], when set, is
 * a pill inline at the bubble's trailing edge; the bubble and overlay take no taps, only the pill does.
 */
@Immutable
data class SpotlightCallout(val text: String, val anchor: Set<Int>, val action: SpotlightAction? = null)
