package com.ivandsky.puzzlebox.profile.settings

import com.ivandsky.puzzlebox.data.visibility.OwnTier
import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import kotlinx.coroutines.flow.Flow

/**
 * The Visibility section's data, handed in by the app shell like the Duels slot (ADR-0027 §10a): [ownTier] is the
 * player's live tier and [onSelect] writes a pick. No slot means no section, and Settings never reaches the repository.
 */
class VisibilitySlot(
    val ownTier: Flow<OwnTier>,
    val onSelect: suspend (VisibilityTier) -> Unit,
)
