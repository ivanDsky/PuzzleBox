package com.ivandsky.puzzlebox.database.linkedin

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The ghosts and fetch_log stores over real in-memory Room: board replace, prune, clear, upsert. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LinkedInDaoTest {

    private lateinit var db: LinkedInDatabase
    private lateinit var ghosts: GhostDao
    private lateinit var log: FetchLogDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LinkedInDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ghosts = db.ghostDao()
        log = db.fetchLogDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun ghost(
        memberRef: String,
        game: String = "queens",
        day: String = "2026-11-01",
        timeMs: Long? = 60_000L,
        hintFree: Boolean = true,
        flawless: Boolean = false,
        fetchedAtMs: Long = 1_000L,
    ) = GhostEntity(
        game = game,
        day = day,
        memberRef = memberRef,
        name = "Name $memberRef",
        timeMs = timeMs,
        hintFree = hintFree,
        flawless = flawless,
        fetchedAtMs = fetchedAtMs,
    )

    private fun entry(
        item: String,
        outcome: String,
        fetchedAtMs: Long = 1_000L,
        profileId: String = "me",
        game: String = "queens",
        day: String = "2026-11-01",
    ) = FetchLogEntity(
        game = game,
        day = day,
        item = item,
        profileId = profileId,
        fetchedAtMs = fetchedAtMs,
        outcome = outcome,
    )

    private suspend fun board(game: String, day: String): Set<GhostEntity> = ghosts.observe(game, day).first().toSet()

    @Test
    fun `replacing a board drops the rows the new board lacks`() = runTest {
        ghosts.replace("queens", "2026-11-01", listOf(ghost("a"), ghost("b")))
        val second = listOf(ghost("b", timeMs = 45_000L, fetchedAtMs = 2_000L), ghost("c", fetchedAtMs = 2_000L))

        ghosts.replace("queens", "2026-11-01", second)

        assertEquals(second.toSet(), board("queens", "2026-11-01"))
    }

    @Test
    fun `replacing a board leaves other days and other games untouched`() = runTest {
        val otherDay = listOf(ghost("a", day = "2026-11-02"))
        val otherGame = listOf(ghost("a", game = "zip"))
        ghosts.replace("queens", "2026-11-01", listOf(ghost("a")))
        ghosts.replace("queens", "2026-11-02", otherDay)
        ghosts.replace("zip", "2026-11-01", otherGame)

        ghosts.replace("queens", "2026-11-01", listOf(ghost("z")))

        assertEquals(otherDay.toSet(), board("queens", "2026-11-02"))
        assertEquals(otherGame.toSet(), board("zip", "2026-11-01"))
    }

    @Test
    fun `replacing with no rows clears only that board`() = runTest {
        val otherGame = listOf(ghost("a", game = "zip"))
        ghosts.replace("queens", "2026-11-01", listOf(ghost("a"), ghost("b")))
        ghosts.replace("zip", "2026-11-01", otherGame)

        ghosts.replace("queens", "2026-11-01", emptyList())

        assertEquals(emptySet<GhostEntity>(), board("queens", "2026-11-01"))
        assertEquals(otherGame.toSet(), board("zip", "2026-11-01"))
    }

    @Test
    fun `pruning drops ghosts of older days and keeps the boundary day`() = runTest {
        val boundary = listOf(ghost("a", day = "2026-11-01"))
        val newer = listOf(ghost("a", day = "2026-11-02"))
        ghosts.replace("queens", "2026-10-31", listOf(ghost("a", day = "2026-10-31")))
        ghosts.replace("zip", "2026-09-30", listOf(ghost("a", game = "zip", day = "2026-09-30")))
        ghosts.replace("queens", "2026-11-01", boundary)
        ghosts.replace("queens", "2026-11-02", newer)

        ghosts.pruneBefore("2026-11-01")

        assertEquals(emptySet<GhostEntity>(), board("queens", "2026-10-31"))
        assertEquals(emptySet<GhostEntity>(), board("zip", "2026-09-30"))
        assertEquals(boundary.toSet(), board("queens", "2026-11-01"))
        assertEquals(newer.toSet(), board("queens", "2026-11-02"))
    }

    @Test
    fun `clearing both stores leaves both tables empty`() = runTest {
        ghosts.replace("queens", "2026-11-01", listOf(ghost("a")))
        ghosts.replace("zip", "2026-11-02", listOf(ghost("b", game = "zip", day = "2026-11-02")))
        log.put(entry("Own", "Solved"))
        log.put(entry("Board", "Board", game = "zip", day = "2026-11-02"))

        ghosts.clear()
        log.clear()

        assertEquals(emptySet<GhostEntity>(), board("queens", "2026-11-01"))
        assertEquals(emptySet<GhostEntity>(), board("zip", "2026-11-02"))
        assertEquals(emptyList<FetchLogEntity>(), log.observe("queens", "2026-11-01", "me").first())
        assertEquals(emptyList<FetchLogEntity>(), log.observe("zip", "2026-11-02", "me").first())
    }

    @Test
    fun `clearing ghosts keeps the fetch log`() = runTest {
        ghosts.replace("queens", "2026-11-01", listOf(ghost("a")))
        log.put(entry("Own", "Solved"))

        ghosts.clear()

        assertEquals(listOf(entry("Own", "Solved")), log.observe("queens", "2026-11-01", "me").first())
    }

    @Test
    fun `putting the same key again overwrites its time and outcome`() = runTest {
        log.put(entry("Board", "BoardToday", fetchedAtMs = 1_000L))

        log.put(entry("Board", "Board", fetchedAtMs = 2_000L))

        assertEquals(
            listOf(entry("Board", "Board", fetchedAtMs = 2_000L)),
            log.observe("queens", "2026-11-01", "me").first(),
        )
    }

    @Test
    fun `entries of another item or profile stay separate`() = runTest {
        log.put(entry("Own", "NotPlayed", fetchedAtMs = 1_000L))
        log.put(entry("Board", "Board", fetchedAtMs = 2_000L))
        log.put(entry("Own", "Solved", fetchedAtMs = 3_000L, profileId = "other"))

        assertEquals(entry("Own", "NotPlayed", fetchedAtMs = 1_000L), log.get("queens", "2026-11-01", "Own", "me"))
        assertEquals(entry("Board", "Board", fetchedAtMs = 2_000L), log.get("queens", "2026-11-01", "Board", "me"))
        assertEquals(
            entry("Own", "Solved", fetchedAtMs = 3_000L, profileId = "other"),
            log.get("queens", "2026-11-01", "Own", "other"),
        )
    }

    @Test
    fun `a missing entry reads as null`() = runTest {
        log.put(entry("Own", "Solved"))

        assertNull(log.get("queens", "2026-11-01", "Board", "me"))
    }

    @Test
    fun `observing the log sees a later put`() = runTest {
        val before = log.observe("queens", "2026-11-01", "me").first()

        log.put(entry("Own", "Solved"))

        assertEquals(emptyList<FetchLogEntity>(), before)
        assertEquals(listOf(entry("Own", "Solved")), log.observe("queens", "2026-11-01", "me").first())
    }

    @Test
    fun `a hidden time and the two badges survive a board write`() = runTest {
        val rows = listOf(
            ghost("hidden", timeMs = null, hintFree = false, flawless = true),
            ghost("timed", timeMs = 73_000L, hintFree = true, flawless = false),
        )

        ghosts.replace("queens", "2026-11-01", rows)

        val stored = ghosts.observe("queens", "2026-11-01").first().associateBy { it.memberRef }
        assertNull(stored.getValue("hidden").timeMs)
        assertEquals(rows.toSet(), stored.values.toSet())
    }

    @Test
    fun `a day without a row of the member gets the member's newest row from any game or day`() = runTest {
        ghosts.replace("queens", "2026-11-01", listOf(ghost("me", fetchedAtMs = 5_000L), ghost("friend", fetchedAtMs = 9_000L)))
        val newest = ghost("me", game = "zip", day = "2026-11-03", fetchedAtMs = 7_000L)
        ghosts.replace("zip", "2026-11-03", listOf(newest))
        ghosts.replace("zip", "2026-11-02", listOf(ghost("me", game = "zip", day = "2026-11-02", fetchedAtMs = 3_000L)))
        val friend = ghost("friend", game = "zip", day = "2026-11-09")
        ghosts.replace("zip", "2026-11-09", listOf(friend))

        assertEquals(setOf(friend, newest), ghosts.observeWithNewest("zip", "2026-11-09", "me").first().toSet())
    }

    @Test
    fun `a day that has the member's row is not joined by a newer row of another day`() = runTest {
        val own = ghost("me", fetchedAtMs = 1_000L)
        ghosts.replace("queens", "2026-11-01", listOf(own))
        ghosts.replace("zip", "2026-11-03", listOf(ghost("me", game = "zip", day = "2026-11-03", fetchedAtMs = 7_000L)))

        assertEquals(listOf(own), ghosts.observeWithNewest("queens", "2026-11-01", "me").first())
    }

    @Test
    fun `a member with no stored row adds nothing`() = runTest {
        val friend = ghost("friend")
        ghosts.replace("queens", "2026-11-01", listOf(friend))

        assertEquals(listOf(friend), ghosts.observeWithNewest("queens", "2026-11-01", "me").first())
        assertEquals(emptyList<GhostEntity>(), ghosts.observeWithNewest("zip", "2026-11-09", "me").first())
    }
}
