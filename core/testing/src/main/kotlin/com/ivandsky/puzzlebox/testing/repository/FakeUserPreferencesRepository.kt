package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [UserPreferencesRepository] with the real one's semantics: one theme mode,
 * a per-game tutorial-seen flag that starts false and, once marked, stays set, and the
 * witness auto-show switch, on by default. Every change re-emits. [seen] reads the marked
 * games synchronously for assertions.
 */
class FakeUserPreferencesRepository(
    seen: Set<GameId> = emptySet(),
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    autoShowWitness: Boolean = true,
) : UserPreferencesRepository {

    private val theme = MutableStateFlow(themeMode)
    private val seenGames = MutableStateFlow(seen)
    private val autoShow = MutableStateFlow(autoShowWitness)

    /** The games whose tutorial is marked seen right now. */
    val seen: Set<GameId> get() = seenGames.value

    override val themeMode: Flow<ThemeMode> = theme

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
}
