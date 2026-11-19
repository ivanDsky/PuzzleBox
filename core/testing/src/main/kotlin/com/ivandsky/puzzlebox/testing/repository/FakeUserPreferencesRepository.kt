package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ThemeMode
import com.ivandsky.puzzlebox.model.ThemeRead
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [UserPreferencesRepository] with the real one's semantics: one theme mode,
 * a per-game tutorial-seen flag that starts false and, once marked, stays set, and the
 * witness auto-show and replay-errors switches, both on by default, the Ranks Friends default (on), the Ranks LinkedIn games default (off) and vibration (on) and the self-duel time-left switch (on). Every change re-emits. [seen] reads the marked
 * games synchronously for assertions.
 */
class FakeUserPreferencesRepository(
    seen: Set<GameId> = emptySet(),
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    autoShowWitness: Boolean = true,
    showReplayErrors: Boolean = true,
    ranksFriendsDefault: Boolean = true,
    ranksLinkedInDefault: Boolean = false,
    vibration: Boolean = true,
    showSelfDuelTimeLeft: Boolean = true,
    private val themeReadFails: Boolean = false,
) : UserPreferencesRepository {

    private val theme = MutableStateFlow(themeMode)
    private val seenGames = MutableStateFlow(seen)
    private val autoShow = MutableStateFlow(autoShowWitness)
    private val replayErrors = MutableStateFlow(showReplayErrors)
    private val ranksFriends = MutableStateFlow(ranksFriendsDefault)
    private val ranksLinkedIn = MutableStateFlow(ranksLinkedInDefault)
    private val vibrationOn = MutableStateFlow(vibration)
    private val selfDuelTimeLeft = MutableStateFlow(showSelfDuelTimeLeft)

    /** The games whose tutorial is marked seen right now. */
    val seen: Set<GameId> get() = seenGames.value

    override val themeMode: Flow<ThemeMode> = theme

    override val themeRead: Flow<ThemeRead> = theme.map { ThemeRead(if (themeReadFails) ThemeMode.SYSTEM else it, themeReadFails) }

    override suspend fun setThemeMode(mode: ThemeMode) {
        theme.value = mode
    }

    override fun tutorialSeen(game: GameId): Flow<Boolean> = seenGames.map { game in it }.distinctUntilChanged()

    override suspend fun markTutorialSeen(game: GameId) {
        seenGames.update { it + game }
    }

    override val autoShowWitness: Flow<Boolean> = autoShow

    override suspend fun setAutoShowWitness(on: Boolean) {
        autoShow.value = on
    }

    override val showReplayErrors: Flow<Boolean> = replayErrors

    override suspend fun setShowReplayErrors(on: Boolean) {
        replayErrors.value = on
    }

    override val ranksFriendsDefault: Flow<Boolean> = ranksFriends

    override suspend fun setRanksFriendsDefault(friends: Boolean) {
        ranksFriends.value = friends
    }

    override val ranksLinkedInDefault: Flow<Boolean> = ranksLinkedIn

    override suspend fun setRanksLinkedInDefault(linkedIn: Boolean) {
        ranksLinkedIn.value = linkedIn
    }

    override val vibration: Flow<Boolean> = vibrationOn

    override suspend fun setVibration(on: Boolean) {
        vibrationOn.value = on
    }

    override val showSelfDuelTimeLeft: Flow<Boolean> = selfDuelTimeLeft

    override suspend fun setShowSelfDuelTimeLeft(on: Boolean) {
        selfDuelTimeLeft.value = on
    }
}
