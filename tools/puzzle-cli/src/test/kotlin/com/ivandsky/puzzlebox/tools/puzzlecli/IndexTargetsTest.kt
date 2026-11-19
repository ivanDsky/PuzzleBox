package com.ivandsky.puzzlebox.tools.puzzlecli

import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Which games the index pass walks: exactly the plugins with a technique walker, named by no code. */
class IndexTargetsTest {

    private val events = mutableListOf<String>()
    private val since = LocalDate.of(2026, 1, 1)

    private fun source(id: GameId) = ScriptedDailySource(id, since, events)

    private val withWalker = PuzzleCli.registry.all.filter { it.techniqueWalker != null }.map { it.definition.id }
    private val withoutWalker = PuzzleCli.registry.all.filter { it.techniqueWalker == null }.map { it.definition.id }

    @Test
    fun `only sources whose plugin has a walker are paired`() {
        val sources = (withWalker + withoutWalker + GameId("not-a-game")).map(::source)
        val targets = IndexTargets.pair(sources, PuzzleCli.registry)
        assertEquals(withWalker.toSet(), targets.map { it.source.gameId }.toSet())
        assertTrue(withWalker.isNotEmpty() && withoutWalker.isNotEmpty())
    }

    @Test
    fun `a game without a walker is never fetched`() {
        val untouched = withoutWalker.map(::source)
        IndexTargets.pair(untouched, PuzzleCli.registry)
        assertTrue(untouched.all { it.fetched.isEmpty() })
        assertTrue(events.isEmpty())
    }

    @Test
    fun `the real root pairs exactly the plugins that have a walker`() {
        val targets = PuzzleCli.indexTargets { error("the pairing makes no request") }
        assertEquals(withWalker.toSet(), targets.map { it.source.gameId }.toSet())
        assertEquals(withWalker.size, targets.size)
    }
}
