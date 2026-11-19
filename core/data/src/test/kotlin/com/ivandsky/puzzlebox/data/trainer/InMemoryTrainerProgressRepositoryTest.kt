package com.ivandsky.puzzlebox.data.trainer

import app.cash.turbine.test
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress.Mark
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryTrainerProgressRepositoryTest {

    private val zip = GameId("zip")
    private val other = GameId("other")
    private val tech = TechniqueId("line-lock")

    @Test
    fun `update on an absent technique sees NEW`() = runTest {
        val repo = InMemoryTrainerProgressRepository()
        var seen: TechniqueProgress? = null
        val stored = repo.update(zip, tech) { seen = it; TechniqueProgress(Mark.Practicing, 1, 9) }
        assertEquals(TechniqueProgress.NEW, seen)
        assertEquals(TechniqueProgress(Mark.Practicing, 1, 9), stored)
    }

    @Test
    fun `observe emits the updated map for its game only`() = runTest {
        val repo = InMemoryTrainerProgressRepository()
        val progress = TechniqueProgress(Mark.Practicing, 1, 9)
        repo.observe(zip).test {
            assertEquals(emptyMap<TechniqueId, TechniqueProgress>(), awaitItem())
            repo.update(other, tech) { progress }
            repo.update(zip, tech) { progress }
            assertEquals(mapOf(tech to progress), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
