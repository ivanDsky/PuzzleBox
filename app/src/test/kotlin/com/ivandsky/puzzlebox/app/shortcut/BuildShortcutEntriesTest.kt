package com.ivandsky.puzzlebox.app.shortcut

import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Test

class BuildShortcutEntriesTest {

    private val build = BuildShortcutEntries()
    private val zip = ShortcutGame(GameId("zip"), "Zip")
    private val queens = ShortcutGame(GameId("queens"), "Queens")

    @Test
    fun `up next comes first and the games follow in the order given`() {
        assertEquals(
            listOf(
                ShortcutEntry(ShortcutTarget.UpNext, gameName = null, rank = 0),
                ShortcutEntry(ShortcutTarget.Game(zip.id), "Zip", rank = 1),
                ShortcutEntry(ShortcutTarget.Game(queens.id), "Queens", rank = 2),
            ),
            build(listOf(zip, queens)),
        )
    }

    @Test
    fun `no games leaves only up next`() {
        assertEquals(listOf(ShortcutEntry(ShortcutTarget.UpNext, gameName = null, rank = 0)), build(emptyList()))
    }

    @Test
    fun `a single game ranks one after up next`() {
        assertEquals(listOf(0, 1), build(listOf(queens)).map { it.rank })
    }
}
