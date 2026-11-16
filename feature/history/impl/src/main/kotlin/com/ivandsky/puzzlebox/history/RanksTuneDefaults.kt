package com.ivandsky.puzzlebox.history

import com.ivandsky.puzzlebox.data.visibility.OwnTier
import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import kotlinx.coroutines.flow.Flow

/**
 * What the Tune sheet shows of the player's defaults, handed in by the app shell like Settings' visibility slot
 * (ADR-0027 §10a): [ownTier] is the player's live visibility tier and [onSelectTier] writes a pick; [friendsDefault] is
 * whether the hub lands on Friends (false: Global) and [onSelectFriendsDefault] writes it. No slot means the sheet
 * offers neither, and the hub never reaches the repositories.
 */
class RanksTuneDefaults(
    val ownTier: Flow<OwnTier>,
    val onSelectTier: suspend (VisibilityTier) -> Unit,
    val friendsDefault: Flow<Boolean>,
    val onSelectFriendsDefault: suspend (Boolean) -> Unit,
)
