package com.ivandsky.puzzlebox.play.friend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.CustomResultKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** What a friend's puzzle result draws: this solve's facts, the stored [puzzle] and the solve's [standing]. */
internal data class CustomResultState(
    val timeMs: Long,
    val hintsUsed: Int,
    val gameName: String,
    val brand: GameBrand,
    /** The friend's puzzle as stored when its code opened; null until read. */
    val puzzle: ReceivedPuzzle? = null,
    /** Null while the puzzle has no completion here yet: the first solve's write is in flight. */
    val standing: CustomStanding? = null,
)

/** Whether a solve set this device's record of a friend's puzzle. */
internal sealed interface CustomStanding {
    /** The first solve here: it is the record. */
    data object Record : CustomStanding

    /** A later solve; the record stays at [recordTimeMs]. */
    data class Replay(val recordTimeMs: Long) : CustomStanding
}

/**
 * One [CustomResultKey]'s result: the solve's own facts ride the key, so the page draws at
 * once and after process death; the stored puzzle and this device's completions of it follow.
 * The solve is the Record when it is the row that stands on the board — the record row, else
 * the oldest — and a Replay otherwise, naming that row's time. The game's name and brand come
 * from the registries; the feature names no game.
 */
internal class CustomResultViewModel(
    private val key: CustomResultKey,
    completions: CompletionRepository,
    received: ReceivedPuzzleRepository,
    games: GameRegistry,
    plays: GamePlayRegistry,
    private val store: PlaySessionStore,
) : ViewModel() {

    private val slot = PlaySlot.Custom(GameId(key.gameId), key.puzzleId)

    private val solve = CustomResultState(
        timeMs = key.timeMs,
        hintsUsed = key.hintsUsed,
        gameName = games.displayName(slot.gameId),
        brand = plays.brandOf(slot.gameId),
    )

    val state: StateFlow<CustomResultState> = combine(
        ShareCode.parse(key.puzzleId)?.let(received::observe) ?: flowOf(null),
        completions.observeCustom(slot.gameId, key.puzzleId),
    ) { puzzle, records -> solve.copy(puzzle = puzzle, standing = standingOf(records)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), solve)

    /**
     * Play again: resets the finished live play, if it is still held, before its board
     * reopens — a board reopened on a complete play would hand the old solve off again.
     */
    fun playAgain() {
        store.peek(slot)?.takeIf { it.controller.isComplete }?.replay()
    }

    /** [records] newest first; the row that stands is the record row, else the oldest. */
    private fun standingOf(records: List<CompletionRecord>): CustomStanding? {
        val stands = records.firstOrNull { it.isRecord } ?: records.lastOrNull() ?: return null
        return if (stands.time == key.timeMs && stands.hints == key.hintsUsed) {
            CustomStanding.Record
        } else {
            CustomStanding.Replay(recordTimeMs = stands.time)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
