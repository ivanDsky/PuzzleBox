package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.navigation.NavigationState

/**
 * Puts shared text in front of Create's root ViewModel (M5-09): selects Create, which pops
 * it to its root when it was already on top, so a deep Create stack never hides under the
 * sheet that consumes the text. The text itself is not carried in the state; the root's
 * ViewModel reads it on resume (M5-10-b hands it over in `MainActivity`).
 */
internal fun NavigationState.applySharedText(): NavigationState = selectTab(CreateKey)
