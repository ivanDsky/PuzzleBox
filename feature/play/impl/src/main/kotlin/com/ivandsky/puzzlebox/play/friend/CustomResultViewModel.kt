package com.ivandsky.puzzlebox.play.friend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
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
    /** "Queens 8×8 · Crown the castle"; the game's name alone until the puzzle is read. */
    val title: String,
    val brand: GameBrand,
    val rankOrder: RankOrder,
    /** The friend's puzzle as stored when its code opened; null until read. */
    val puzzle: ReceivedPuzzle? = null,
    /** Null while the puzzle has no completion here yet: the first solve's write is in flight. */
    val standing: CustomStanding? = null,
)

/** Whether a solve set this device's record of a friend's puzzle. */
internal sealed interface CustomStanding {
    /** The first solve here, or one that beat the record: it is the record. */
    data object Record : CustomStanding

    /** A later solve that left [record] standing. */
    data class Replay(val record: BestScore) : CustomStanding
}

/**
 * One [CustomResultKey]'s result: the solve's own facts ride the key, so the page draws at
 * once and after process death; the stored puzzle and this device's completions of it follow.
 * The solve is the Record when it is the row that stands on the board — the record row, else
 * the oldest — and a Replay otherwise, naming that row's score. The game's name, size, brand
 * and rank order come from the registries; the feature names no game.
 */
internal class CustomResultViewModel(
    private val key: CustomResultKey,
    completions: CompletionRepository,
    received: ReceivedPuzzleRepository,
    games: GameRegistry,
    plays: GamePlayRegistry,
    editors: GameEditorRegistry,
    private val store: PlaySessionStore,
) : ViewModel() {

    private val slot = PlaySlot.Custom(GameId(key.gameId), key.puzzleId)

    private val gameName = games.displayName(slot.gameId)

    private val solve = CustomResultState(
        timeMs = key.timeMs,
        hintsUsed = key.hintsUsed,
        title = gameName,
        brand = plays.brandOf(slot.gameId),
        rankOrder = games[slot.gameId]?.score?.rankOrder ?: RankOrder.TimeThenHints,
    )

    val state: StateFlow<CustomResultState> = combine(
        ShareCode.parse(key.puzzleId)?.let(received::observe) ?: flowOf(null),
        completions.observeCustom(slot.gameId, key.puzzleId),
    ) { puzzle, records ->
        solve.copy(
            title = puzzle?.let {
                val size = editors[it.gameId]?.sizeLabel(it.variant) ?: it.variant.value
                "$gameName $size · ${it.title}"
            } ?: gameName,
            puzzle = puzzle,
            standing = standingOf(records),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), solve)

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
            CustomStanding.Replay(record = BestScore(stands.time, stands.hints))
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
