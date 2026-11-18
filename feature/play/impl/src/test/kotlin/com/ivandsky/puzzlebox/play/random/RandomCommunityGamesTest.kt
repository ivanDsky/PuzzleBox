package com.ivandsky.puzzlebox.play.random

import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.play.FakeGames
import com.ivandsky.puzzlebox.play.community.ALPHA
import com.ivandsky.puzzlebox.play.community.BETA
import com.ivandsky.puzzlebox.play.community.NO_EDITOR
import com.ivandsky.puzzlebox.play.community.editorGame
import com.ivandsky.puzzlebox.testing.game.TestGames
import org.junit.Assert.assertEquals
import org.junit.Test

/** New today's Game chips: the games that have an editor, as the Community tab's filter lists them. */
class RandomCommunityGamesTest {
    private val games = GameRegistry(listOf(editorGame(ALPHA), TestGames.plugin(NO_EDITOR), editorGame(BETA)))
    private val plays = FakeGames(listOf(ALPHA, BETA, NO_EDITOR)).registry

    @Test
    fun `only games with an editor are offered, in registry order, with their name and brand`() {
        val offered = communityGamesOf(games, plays)

        assertEquals(listOf(ALPHA, BETA), offered.map(RandomGame::id))
        assertEquals(listOf(games.displayName(ALPHA), games.displayName(BETA)), offered.map(RandomGame::name))
        assertEquals(listOf(plays.brandOf(ALPHA), plays.brandOf(BETA)), offered.map(RandomGame::brand))
    }
}
