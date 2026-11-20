package com.ivandsky.puzzlebox.app.shortcut

import com.ivandsky.puzzlebox.domain.TodayEntry
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveShortcutLandingTest {

    private val resolve = ResolveShortcutLanding()

    private fun entry(id: String, status: TodayStatus) = TodayEntry(GameId(id), id, status)
    private val notStarted = TodayStatus.NotStarted
    private val inProgress = TodayStatus.InProgress(elapsedMs = 1_000)
    private val solved = TodayStatus.Solved(timeMs = 2_000)

    @Test
    fun `up next prefers the first in-progress game over an earlier not-started one`() {
        val today = listOf(entry("zip", notStarted), entry("queens", inProgress), entry("tango", inProgress))

        assertEquals(ShortcutLanding.Board(GameId("queens")), resolve(ShortcutTarget.UpNext, today))
    }

    @Test
    fun `up next skips solved games and takes the first not-started or offline one`() {
        val today = listOf(entry("zip", solved), entry("queens", TodayStatus.Offline), entry("tango", notStarted))

        assertEquals(ShortcutLanding.Board(GameId("queens")), resolve(ShortcutTarget.UpNext, today))
    }

    @Test
    fun `up next with everything solved lands on Today`() {
        val today = listOf(entry("zip", solved), entry("queens", solved))

        assertEquals(ShortcutLanding.Today, resolve(ShortcutTarget.UpNext, today))
    }

    @Test
    fun `up next with an empty set lands on Today`() {
        assertEquals(ShortcutLanding.Today, resolve(ShortcutTarget.UpNext, emptyList()))
    }

    @Test
    fun `a game target is always its board whatever its status`() {
        val today = listOf(entry("zip", solved))

        assertEquals(ShortcutLanding.Board(GameId("zip")), resolve(ShortcutTarget.Game(GameId("zip")), today))
        assertEquals(ShortcutLanding.Board(GameId("queens")), resolve(ShortcutTarget.Game(GameId("queens")), emptyList()))
    }
}
