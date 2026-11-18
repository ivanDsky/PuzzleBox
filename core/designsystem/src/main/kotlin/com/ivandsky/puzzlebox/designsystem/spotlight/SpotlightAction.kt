package com.ivandsky.puzzlebox.designsystem.spotlight

import androidx.compose.runtime.Immutable

/** The one label of a hint's action: every game offers "Place it", so none can word it differently. */
const val PLACE_IT = "Place it"

/**
 * The action a [SpotlightCallout] offers, drawn as a pill at its trailing edge: [label], and [onClick] for a tap on the pill
 * alone. It carries no game or hint type; compares by its lambda's identity.
 */
@Immutable
data class SpotlightAction(val label: String, val onClick: () -> Unit)
