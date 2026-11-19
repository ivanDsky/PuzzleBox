package com.ivandsky.puzzlebox.domain.trainer

import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress.Mark
import com.ivandsky.puzzlebox.data.trainer.TrainerProgressRepository
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.model.GameId
import java.time.Clock

/**
 * Applies one trainer answer to a technique's progress. Right raises the in-a-row count and
 * reaches Mastered at 5; Another way leaves the count; Wrong resets it; Show me resets it and
 * leaves the mark (a row with a Show me inside is not a row). Any verdict moves New to
 * Practicing, Show me does not, and Mastered never drops. Every answer stamps the clock's time.
 */
class RecordAnswer(
    private val progress: TrainerProgressRepository,
    private val clock: Clock = Clock.systemUTC(),
) {

    /** What the player did with a snippet. */
    sealed interface Answer {
        /** The player made the move and it was judged. */
        data class Verdict(val verdict: TrainerVerdict) : Answer

        /** The player asked to be shown the move. */
        data object ShowMe : Answer
    }

    /** Records [answer] for [technique] in [game]; returns the stored progress. */
    suspend operator fun invoke(game: GameId, technique: TechniqueId, answer: Answer): TechniqueProgress {
        val now = clock.millis()
        return progress.update(game, technique) { current -> apply(current, answer, now) }
    }

    private fun apply(current: TechniqueProgress, answer: Answer, now: Long): TechniqueProgress {
        val practicing = maxOf(current.mark, Mark.Practicing)
        return when (answer) {
            Answer.ShowMe -> TechniqueProgress(current.mark, 0, now)
            is Answer.Verdict -> when (answer.verdict) {
                TrainerVerdict.Right -> {
                    val row = current.inRow + 1
                    TechniqueProgress(if (row >= MASTERED_ROW) Mark.Mastered else practicing, row, now)
                }
                TrainerVerdict.AnotherWay -> TechniqueProgress(practicing, current.inRow, now)
                TrainerVerdict.Wrong -> TechniqueProgress(practicing, 0, now)
            }
        }
    }

    private companion object {
        const val MASTERED_ROW = 5
    }
}
