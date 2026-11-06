package com.ivandsky.puzzlebox.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GameRegistryTest {

    private val plugin = TestGame.plugin()

    @Test
    fun `an unlisted game is off all but still resolves by id`() {
        val registry = GameRegistry(listOf(plugin), unlisted = setOf(TestGame.id))

        assertEquals(emptyList<GamePlugin<*, *, *>>(), registry.all)
        assertEquals(listOf(plugin), registry.unlisted)
        assertSame(plugin, registry[TestGame.id])
        assertEquals(TestGame.Definition.displayName, registry.displayName(TestGame.id))
    }

    @Test
    fun `games are listed by default`() {
        val registry = GameRegistry(listOf(plugin))

        assertEquals(listOf(plugin), registry.all)
        assertEquals(emptyList<GamePlugin<*, *, *>>(), registry.unlisted)
    }
}
