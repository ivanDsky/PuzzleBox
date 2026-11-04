package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import org.junit.Assert.assertEquals
import org.junit.Test

/** The featured game is the first registered plugin; an empty registry still composes. */
class GetFeaturedGameTest {

    @Test
    fun `the first registered game is featured`() {
        assertEquals(
            GameSummary(beta, "Beta"),
            GetFeaturedGame(TestGames.registry(listOf(beta, alpha)))(),
        )
    }

    @Test
    fun `an empty registry falls back to zip`() {
        assertEquals(GameSummary(GameId("zip"), "Zip"), GetFeaturedGame(GameRegistry(emptyList()))())
    }
}
