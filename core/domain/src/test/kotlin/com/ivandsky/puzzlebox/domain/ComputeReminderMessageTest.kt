package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ReminderMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComputeReminderMessageTest {

    private val message = ComputeReminderMessage()

    private fun entry(name: String, status: TodayStatus) = TodayEntry(GameId(name.lowercase()), name, status)

    @Test
    fun `playable games are listed in the given order with the exact copy`() {
        val entries = listOf(
            entry("Queens", TodayStatus.NotStarted),
            entry("Tango", TodayStatus.InProgress(5_000L)),
            entry("Zip", TodayStatus.NotStarted),
        )

        assertEquals(
            ReminderMessage("Today's puzzles are waiting", "Open today: Queens, Tango, Zip."),
            message(entries),
        )
    }

    @Test
    fun `one playable game reads as a single name`() {
        assertEquals("Open today: Zip.", message(listOf(entry("Zip", TodayStatus.NotStarted)))?.body)
    }

    @Test
    fun `any solved puzzle skips the reminder`() {
        val entries = listOf(entry("Queens", TodayStatus.Solved(60_000L)), entry("Zip", TodayStatus.NotStarted))

        assertNull(message(entries))
    }

    @Test
    fun `an offline game is left out`() {
        val entries = listOf(entry("Queens", TodayStatus.Offline), entry("Zip", TodayStatus.NotStarted))

        assertEquals("Open today: Zip.", message(entries)?.body)
    }

    @Test
    fun `no playable game skips the reminder`() {
        assertNull(message(listOf(entry("Queens", TodayStatus.Offline), entry("Zip", TodayStatus.Offline))))
        assertNull(message(emptyList()))
    }
}
