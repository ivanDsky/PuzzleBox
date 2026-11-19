package com.ivandsky.puzzlebox.train.domain

import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress.Mark
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.repository.FakeTrainerProgressRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordAnswerTest {

    private val game = GameId("zip")
    private val technique = TechniqueId("line-lock")
    private val now = 1_700_000_000_000L
    private val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)

    private val right = RecordAnswer.Answer.Verdict(TrainerVerdict.Right)
    private val anotherWay = RecordAnswer.Answer.Verdict(TrainerVerdict.AnotherWay)
    private val wrong = RecordAnswer.Answer.Verdict(TrainerVerdict.Wrong)
    private val showMe = RecordAnswer.Answer.ShowMe

    private fun p(mark: Mark, row: Int, at: Long = 5L) = TechniqueProgress(mark, row, at)

    @Test
    fun `each start and answer gives the expected progress`() = runTest {
        val table = listOf(
            Triple(TechniqueProgress.NEW, right, p(Mark.Practicing, 1, now)),
            Triple(p(Mark.Practicing, 4), right, p(Mark.Mastered, 5, now)),
            Triple(p(Mark.Mastered, 7), wrong, p(Mark.Mastered, 0, now)),
            Triple(p(Mark.Mastered, 7), showMe, p(Mark.Mastered, 0, now)),
            Triple(p(Mark.Mastered, 7), anotherWay, p(Mark.Mastered, 7, now)),
            Triple(p(Mark.Practicing, 3), anotherWay, p(Mark.Practicing, 3, now)),
            Triple(TechniqueProgress.NEW, anotherWay, p(Mark.Practicing, 0, now)),
            Triple(p(Mark.Practicing, 3), wrong, p(Mark.Practicing, 0, now)),
            Triple(TechniqueProgress.NEW, wrong, p(Mark.Practicing, 0, now)),
            Triple(p(Mark.Practicing, 3), showMe, p(Mark.Practicing, 0, now)),
            Triple(TechniqueProgress.NEW, showMe, p(Mark.New, 0, now)),
        )
        for ((start, answer, expected) in table) {
            val repo = FakeTrainerProgressRepository()
            repo.update(game, technique) { start }
            val result = RecordAnswer(repo, clock)(game, technique, answer)
            assertEquals("$start + $answer", expected, result)
            assertEquals(expected, repo.stored.getValue(game).getValue(technique))
        }
    }

    @Test
    fun `a Show me inside a row stops it reaching mastery`() = runTest {
        val record = RecordAnswer(FakeTrainerProgressRepository(), clock)
        repeat(4) { record(game, technique, right) }
        record(game, technique, showMe)
        assertEquals(p(Mark.Practicing, 1, now), record(game, technique, right))
    }
}
