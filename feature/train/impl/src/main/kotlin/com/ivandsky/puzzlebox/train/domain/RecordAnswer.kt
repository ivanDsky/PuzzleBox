package com.ivandsky.puzzlebox.train.domain

import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress.Mark
import com.ivandsky.puzzlebox.data.trainer.TrainerProgressRepository
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.model.GameId
import java.time.Clock

/**
 * Applies one practice turn's [TrainerVerdict] to a technique's progress. Right raises the in-a-row count and reaches
 * Mastered at 5; Wrong resets it; Show me resets it and leaves the mark (a row with a Show me inside is not a row).
 * Right and Wrong move New to Practicing, Show me does not, and Mastered never drops. Every verdict stamps the clock's
 * time.
 */
class RecordAnswer(
    private val progress: TrainerProgressRepository,
    private val clock: Clock = Clock.systemUTC(),
) {

    /** Records [verdict] for [technique] in [game]; returns the stored progress. */
    suspend operator fun invoke(game: GameId, technique: TechniqueId, verdict: TrainerVerdict): TechniqueProgress {
        val now = clock.millis()
        return progress.update(game, technique) { current -> apply(current, verdict, now) }
    }

    private fun apply(current: TechniqueProgress, verdict: TrainerVerdict, now: Long): TechniqueProgress {
        val practicing = maxOf(current.mark, Mark.Practicing)
        return when (verdict) {
            TrainerVerdict.Right -> {
                val row = current.inRow + 1
                TechniqueProgress(if (row >= MASTERED_ROW) Mark.Mastered else practicing, row, now)
            }
            TrainerVerdict.Wrong -> TechniqueProgress(practicing, 0, now)
            TrainerVerdict.ShowMe -> TechniqueProgress(current.mark, 0, now)
        }
    }

    private companion object {
        const val MASTERED_ROW = 5
    }
}
