package com.ivandsky.puzzlebox.backend.firebase

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [FirestoreLeaderboardStore.count] against the Firestore emulator under the deployed rules: the "of M" of a board
 * is the aggregate over `leaderboards/{board}/entries`. Skipped when the emulators are not up (see
 * [FirestoreReplayDocStoreEmulatorTest]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LeaderboardCountEmulatorTest {

    private val store = FirestoreLeaderboardStore()

    @Before
    fun freshEmulator() = EmulatorBackend.startClean()

    @Test
    fun `an empty board counts zero`() = EmulatorBackend.blocking {
        EmulatorBackend.signInAsNewUser()

        assertEquals(0L, store.count(EmulatorBackend.dailyBoardId()))
    }

    @Test
    fun `a board counts exactly its entries and no other board's`() = EmulatorBackend.blocking {
        val day = LocalDate.now(ZoneOffset.UTC)
        repeat(3) { n -> postEntry("zip", day, timeMs = 1_000L + n) }
        postEntry("tango", day, timeMs = 5_000L)

        assertEquals(3L, store.count("zip_$day"))
        assertEquals(1L, store.count("tango_$day"))
    }

    private suspend fun postEntry(game: String, day: LocalDate, timeMs: Long) {
        val uid = EmulatorBackend.signInAsNewUser()
        val entry = LeaderboardEntry(uid, "Player", "$game/$day/1", timeMs, hints = 0, completedAtMs = 1_700_000_000_000L)
        check(store.createIfAbsent("${game}_$day", uid, LeaderboardDocCodec.encode(entry)))
    }
}
