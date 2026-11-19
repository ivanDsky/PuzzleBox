package com.ivandsky.puzzlebox.data.cloud.kinds

import androidx.room.Room
import com.ivandsky.puzzlebox.data.cloud.CloudDoc
import com.ivandsky.puzzlebox.database.MatchLocalEntity
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.database.SelfMatchEntity
import com.ivandsky.puzzlebox.testing.room.RoomQueryThread
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Seen reveals as a synced kind over in-memory Room: the doc shape, who is pending, and that the first reveal wins. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RevealsKindTest {

    private lateinit var db: PuzzleBoxDatabase
    private lateinit var kind: RevealsKind

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(RoomQueryThread.executor)
            .build()
        kind = RevealsKind(db, db.matchLocalDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val dao get() = db.matchLocalDao()

    /** A duel row of [code], its reveal seen at [seenAt] under [owner] (not seen when [seenAt] is null). */
    private suspend fun duel(code: String, seenAt: Long?, owner: String? = "ann", my: Int? = 3, their: Int? = 1) {
        dao.insertIfAbsent(MatchLocalEntity(code, "Invite", "Sam", openedAt = 1_000L, revealSeenAt = null))
        if (seenAt != null) dao.markRevealSeen(code, seenAt, my, their, owner)
    }

    private suspend fun row(code: String) = dao.observeAll().first().single { it.code == code }

    private fun doc(code: String, seenAt: Any? = 9_000L, my: Any? = 2L, their: Any? = 2L) = CloudDoc(
        code,
        buildMap {
            put("formatVersion", 1L)
            put("seenAtMs", seenAt)
            if (my != null) put("myScore", my)
            if (their != null) put("theirScore", their)
        },
    )

    @Test
    fun `a seen reveal of the account is pending as its doc, scores absent when the reveal kept none`() = runTest {
        duel("D7Q2MX", seenAt = 5_000L)
        duel("K4M8RT", seenAt = 6_000L, my = null, their = null)

        assertEquals(setOf("D7Q2MX", "K4M8RT"), kind.pendingIds("ann").first())
        assertEquals(
            listOf(
                CloudDoc("D7Q2MX", mapOf("formatVersion" to 1L, "seenAtMs" to 5_000L, "myScore" to 3L, "theirScore" to 1L)),
                CloudDoc("K4M8RT", mapOf("formatVersion" to 1L, "seenAtMs" to 6_000L)),
            ),
            kind.pending("ann").sortedBy { it.id },
        )
    }

    @Test
    fun `an unseen duel, another account's reveal and a self duel's reveal are never pending`() = runTest {
        duel("D7Q2MX", seenAt = null)
        duel("K4M8RT", seenAt = 6_000L, owner = "bob")
        duel("P9W3ZB", seenAt = 7_000L)
        db.selfMatchDao().insertMatch(SelfMatchEntity("P9W3ZB", 1_000L, 3, "zip", false, "[]"))

        assertEquals(emptySet<String>(), kind.pendingIds("ann").first())
        assertEquals(emptyList<CloudDoc>(), kind.pending("ann"))
    }

    @Test
    fun `adopt claims a reveal seen signed out, not an unseen duel nor another account's reveal`() = runTest {
        duel("D7Q2MX", seenAt = 5_000L, owner = null)
        duel("K4M8RT", seenAt = null)
        duel("P9W3ZB", seenAt = 7_000L, owner = "bob")

        kind.adopt("ann")

        assertEquals(setOf("D7Q2MX"), kind.pendingIds("ann").first())
        assertEquals(null, row("K4M8RT").ownerUid)
        assertEquals(setOf("P9W3ZB"), kind.pendingIds("bob").first())
    }

    @Test
    fun `acknowledge settles the uploaded reveals and forget makes them pending again`() = runTest {
        duel("D7Q2MX", seenAt = 5_000L)
        duel("K4M8RT", seenAt = 6_000L)

        kind.acknowledge("ann", kind.pending("ann").filter { it.id == "D7Q2MX" })
        assertEquals(setOf("K4M8RT"), kind.pendingIds("ann").first())

        kind.forget("ann")
        assertEquals(setOf("D7Q2MX", "K4M8RT"), kind.pendingIds("ann").first())
    }

    @Test
    fun `a restored reveal of an unknown duel is a seen duel with the doc's scores, already in the cloud`() = runTest {
        assertEquals(2, kind.apply("ann", listOf(doc("D7Q2MX"), doc("K4M8RT", my = null, their = null))))

        val restored = row("D7Q2MX")
        assertEquals("Participant", restored.role)
        assertEquals(9_000L, restored.revealSeenAt)
        assertEquals(2, restored.revealMyScore)
        assertEquals(2, restored.revealTheirScore)
        assertEquals("ann", restored.ownerUid)
        assertEquals(null, row("K4M8RT").revealMyScore)
        assertEquals(emptySet<String>(), kind.pendingIds("ann").first())
    }

    @Test
    fun `a restored reveal stamps a known unseen duel and keeps its role and opponent`() = runTest {
        duel("D7Q2MX", seenAt = null)

        assertEquals(1, kind.apply("ann", listOf(doc("D7Q2MX"))))

        val stamped = row("D7Q2MX")
        assertEquals(Triple("Invite", "Sam", 9_000L), Triple(stamped.role, stamped.opponentName, stamped.revealSeenAt))
        assertEquals(emptySet<String>(), kind.pendingIds("ann").first())
    }

    @Test
    fun `a restored reveal never replaces the one this device saw first, and this device stops uploading its own`() = runTest {
        duel("D7Q2MX", seenAt = 5_000L)

        assertEquals(0, kind.apply("ann", listOf(doc("D7Q2MX"))))

        val kept = row("D7Q2MX")
        assertEquals(Triple(5_000L, 3, 1), Triple(kept.revealSeenAt, kept.revealMyScore, kept.revealTheirScore))
        assertEquals(emptySet<String>(), kind.pendingIds("ann").first())
    }

    @Test
    fun `a reveal seen signed out is claimed by the restored doc of the account`() = runTest {
        duel("D7Q2MX", seenAt = 5_000L, owner = null)

        kind.apply("ann", listOf(doc("D7Q2MX")))
        kind.adopt("ann")

        assertEquals("ann", row("D7Q2MX").ownerUid)
        assertEquals(emptySet<String>(), kind.pendingIds("ann").first())
    }

    @Test
    fun `another account's restored doc leaves this device's reveal of a different account alone`() = runTest {
        duel("D7Q2MX", seenAt = 5_000L, owner = "bob")

        kind.apply("ann", listOf(doc("D7Q2MX")))

        assertEquals("bob", row("D7Q2MX").ownerUid)
        assertEquals(setOf("D7Q2MX"), kind.pendingIds("bob").first())
    }

    @Test
    fun `a doc with a bad id, a bad seen time or another format restores nothing, and a bad score reads as none`() = runTest {
        val docs = listOf(
            doc("d7q2mx"),
            doc("NOT-A-CODE"),
            doc("K4M8RT", seenAt = "9000"),
            doc("P9W3ZB", seenAt = -1L),
            doc("H6J2NB").let { it.copy(fields = it.fields + ("formatVersion" to 2L)) },
        )

        assertEquals(0, kind.apply("ann", docs))
        assertTrue(dao.observeAll().first().isEmpty())

        kind.apply("ann", listOf(doc("D7Q2MX", my = 11L, their = "2")))
        val unscored = row("D7Q2MX")
        assertEquals(Pair(null, null), Pair(unscored.revealMyScore, unscored.revealTheirScore))
    }
}
