package com.ivandsky.puzzlebox.designsystem.spotlight

import androidx.compose.runtime.Immutable

/** Text drawn in the inverse-surface callout; [anchor] = cells it sits beside, never over a lit cell. */
@Immutable
data class SpotlightCallout(val text: String, val anchor: Set<Int>)
