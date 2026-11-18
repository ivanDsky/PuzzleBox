package com.ivandsky.puzzlebox.profile.whatsnew

/** What the What's new page shows: the [releases], newest first, or [loadFailed] (then [releases] is empty). */
internal data class WhatsNewUiState(val releases: List<WhatsNewCard> = emptyList(), val loadFailed: Boolean = false)
