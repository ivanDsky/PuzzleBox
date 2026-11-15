package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ThemeMode
import com.ivandsky.puzzlebox.model.ThemeRead
import kotlinx.coroutines.flow.Flow

/**
 * User-level app preferences. Interfaces live in core; features see this only.
 * Backed by Preferences DataStore (small key-value state; Room stays for puzzle data
 * and a settings table would force a schema-v2 migration that is deferred).
 */
interface UserPreferencesRepository {
    val themeMode: Flow<ThemeMode>

    /** [themeMode] that survives a failed read: the default theme with the failure flagged, so startup can release. */
    val themeRead: Flow<ThemeRead>

    suspend fun setThemeMode(mode: ThemeMode)

    /** True once the player finished or skipped [game]'s tutorial; false before that. */
    fun tutorialSeen(game: GameId): Flow<Boolean>

    suspend fun markTutorialSeen(game: GameId)

    /** Whether the Studio opens each 2+ solutions verdict with its witness on the board; true until switched off. */
    val autoShowWitness: Flow<Boolean>

    suspend fun setAutoShowWitness(on: Boolean)

    /** Whether replay boards mark rule violations, for every replay; true until switched off. */
    val showReplayErrors: Flow<Boolean>

    suspend fun setShowReplayErrors(on: Boolean)

    /** Whether the Ranks hub opens on Friends (true, until switched) or Global; a restored selection still wins. */
    val ranksFriendsDefault: Flow<Boolean>

    suspend fun setRanksFriendsDefault(friends: Boolean)
}
