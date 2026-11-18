package com.ivandsky.puzzlebox.play.random

import com.ivandsky.puzzlebox.domain.random.RandomPick
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.community.DifficultyChoice

/** Where a random pick draws from. */
internal enum class RandomSource { Past, Community }

/** Why a pick said nothing, one value per sentence the sheet shows. */
internal enum class RandomMessage { Offline, Throttled, NothingLeftPast, NothingLeftCommunity, NoneOpened, Refused }

/** Where the sheet's pick stands. */
internal sealed interface RandomPhase {
    data object Idle : RandomPhase

    data object Picking : RandomPhase

    /** [pick] was chosen and opened; the sheet hands it to the app. */
    data class Opened(val pick: RandomPick) : RandomPhase

    data class Said(val message: RandomMessage) : RandomPhase
}

/** The sheet's filters and phase. [game] null is Any game; [difficulty] null is any, and applies to Community only. */
internal data class RandomPuzzleState(
    val source: RandomSource,
    val game: GameId?,
    val unsolvedOnly: Boolean,
    val difficulty: DifficultyChoice?,
    val phase: RandomPhase,
)
