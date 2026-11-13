package com.ivandsky.puzzlebox.data.cloud.kinds

import androidx.room.Room
import app.cash.turbine.test
import com.ivandsky.puzzlebox.data.cloud.CloudDoc
import com.ivandsky.puzzlebox.database.PublishedPuzzleEntity
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [PublishedKind] over in-memory Room: the merge table of restore (`unpublished` is one-way, local fields win),
 * pending and acknowledge by `uploadedFingerprint`, and the doc keys that may travel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PublishedKindTest {

    private val db: PuzzleBoxDatabase = Room
        .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val dao = db.publishedPuzzleDao()
    private val kind = PublishedKind(db, dao)

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `the kind is published`() {
        assertEquals("published", kind.kind)
    }

    @Test
    fun `restore inserts an unknown code owned by the uid and already acknowledged`() = runTest {
        val restored = kind.apply("ann", listOf(doc("AAA111", name = "Cats", at = 7, unpublished = false)))

        assertEquals(1, restored)
        val row = row("AAA111")
        assertEquals("ann", row.ownerUid)
        assertEquals("zip", row.gameId)
        assertEquals("Cats", row.title)
        assertEquals(7L, row.createdAt)
        assertEquals("zip/6", row.variant)
        assertEquals(42_000L, row.authorTimeMs)
        assertEquals(false, row.unpublished)
        assertNull(row.solvesCount)
        assertNull(row.solvesCountAt)
        assertNotNull("restored row counts as uploaded", row.uploadedFingerprint)
        assertTrue("a restored row is not pending", kind.pending("ann").isEmpty())
    }

    @Test
    fun `restore of several codes counts every inserted row`() = runTest {
        val restored = kind.apply("ann", listOf(doc("AAA111"), doc("BBB222"), doc("CCC333")))

        assertEquals(3, restored)
        assertEquals(listOf("AAA111", "BBB222", "CCC333"), dao.owned("ann").map { it.code }.sorted())
    }

    @Test
    fun `unpublished never flips back to false`() = runTest {
        seed("AAA111", unpublished = true, owner = "ann", fingerprint = "old")

        val restored = kind.apply("ann", listOf(doc("AAA111", unpublished = false)))

        assertEquals(0, restored)
        assertEquals(true, row("AAA111").unpublished)
    }

    @Test
    fun `a cloud unpublished mark reaches a published local row`() = runTest {
        seed("AAA111", unpublished = false, owner = "ann")

        val restored = kind.apply("ann", listOf(doc("AAA111", unpublished = true)))

        assertEquals(1, restored)
        assertEquals(true, row("AAA111").unpublished)
    }

    @Test
    fun `other fields keep the local value when the row exists`() = runTest {
        seed("AAA111", title = "Mine", createdAt = 5, gameId = "tango", owner = "ann")
        dao.updateSolves("AAA111", 9, 100)

        val restored = kind.apply("ann", listOf(doc("AAA111", name = "Theirs", at = 99, gameId = "zip")))

        assertEquals(0, restored)
        val row = row("AAA111")
        assertEquals("Mine", row.title)
        assertEquals(5L, row.createdAt)
        assertEquals("tango", row.gameId)
        assertEquals(9, row.solvesCount)
    }

    @Test
    fun `merging keeps the local solve count while unpublished flips`() = runTest {
        seed("AAA111", owner = "ann")
        dao.updateSolves("AAA111", 4, 50)

        kind.apply("ann", listOf(doc("AAA111", unpublished = true)))

        val row = row("AAA111")
        assertEquals(4, row.solvesCount)
        assertEquals(50L, row.solvesCountAt)
    }

    @Test
    fun `a local row that differs from the cloud stays pending after restore`() = runTest {
        seed("AAA111", title = "Mine", owner = "ann", fingerprint = "old")

        kind.apply("ann", listOf(doc("AAA111", name = "Theirs")))

        assertEquals(listOf("AAA111"), kind.pending("ann").map { it.id })
        assertEquals("Mine", kind.pending("ann").single().fields["name"])
    }

    @Test
    fun `a local row equal to the cloud after the merge is acknowledged`() = runTest {
        seed("AAA111", title = "Same", createdAt = 7, owner = "ann")

        kind.apply("ann", listOf(doc("AAA111", name = "Same", at = 7, unpublished = true)))

        assertTrue(kind.pending("ann").isEmpty())
    }

    @Test
    fun `a row of another account is never overwritten`() = runTest {
        seed("AAA111", title = "Bob's", owner = "bob")

        val restored = kind.apply("ann", listOf(doc("AAA111", name = "Ann's", unpublished = true)))

        assertEquals(0, restored)
        val row = row("AAA111")
        assertEquals("bob", row.ownerUid)
        assertEquals("Bob's", row.title)
        assertEquals(false, row.unpublished)
    }

    @Test
    fun `docs that cannot be read are skipped and the rest still restore`() = runTest {
        val good = doc("GGG111")
        val restored = kind.apply(
            "ann",
            listOf(
                CloudDoc("BAD", doc("BAD").fields),
                CloudDoc("MXSSN1", mapOf("formatVersion" to 1L, "gameId" to "zip")),
                CloudDoc("WRNG11", doc("WRNG11").fields + ("publishedAtMs" to "soon")),
                CloudDoc("FUTR11", doc("FUTR11").fields + ("formatVersion" to 2L)),
                good,
            ),
        )

        assertEquals(1, restored)
        assertEquals(listOf("GGG111"), dao.owned("ann").map { it.code })
    }

    @Test
    fun `pending is the owner's rows never uploaded or changed since`() = runTest {
        seed("NEW111", owner = "ann")
        seed("OLD222", owner = "ann")
        seed("BOB333", owner = "bob")
        seed("NONE44", owner = null)
        kind.acknowledge("ann", listOf(CloudDoc("OLD222", encoded("OLD222"))))
        seed("EDIT55", title = "Before", owner = "ann")
        kind.acknowledge("ann", listOf(CloudDoc("EDIT55", encoded("EDIT55", title = "Before"))))
        dao.upsert(entity("EDIT55", title = "After", owner = "ann", fingerprint = row("EDIT55").uploadedFingerprint))

        assertEquals(listOf("EDIT55", "NEW111"), kind.pending("ann").map { it.id }.sorted())
        assertEquals(listOf("BOB333"), kind.pending("bob").map { it.id })
    }

    @Test
    fun `a pending doc carries exactly the cloud keys and no local-only column`() = runTest {
        seed("AAA111", title = "Cats", gameId = "zip", createdAt = 7, unpublished = true, owner = "ann")
        dao.updateSolves("AAA111", 3, 10)

        val doc = kind.pending("ann").single()

        assertEquals("AAA111", doc.id)
        assertEquals(
            mapOf(
                "formatVersion" to 1L,
                "gameId" to "zip",
                "name" to "Cats",
                "publishedAtMs" to 7L,
                "unpublished" to true,
                "variant" to "zip/6",
                "authorTimeMs" to 42_000L,
            ),
            doc.fields,
        )
    }

    @Test
    fun `a legacy doc without variant and authorTimeMs restores with the empty values and stays acknowledged`() = runTest {
        val legacy = doc("AAA111", variant = null, authorTimeMs = null)
        assertEquals(setOf("formatVersion", "gameId", "name", "publishedAtMs", "unpublished"), legacy.fields.keys)

        val restored = kind.apply("ann", listOf(legacy))

        assertEquals(1, restored)
        val row = row("AAA111")
        assertEquals("", row.variant)
        assertEquals(0L, row.authorTimeMs)
        assertTrue("a restored legacy row is not pending", kind.pending("ann").isEmpty())
    }

    @Test
    fun `a row with no variant or author time uploads neither key`() = runTest {
        dao.upsert(copyWithoutDetails(entity("AAA111", owner = "ann")))

        val fields = kind.pending("ann").single().fields

        assertEquals(setOf("formatVersion", "gameId", "name", "publishedAtMs", "unpublished"), fields.keys)
    }

    @Test
    fun `a published row's variant and author time survive a round trip through the cloud`() = runTest {
        seed("AAA111", owner = "ann")
        val uploaded = kind.pending("ann").single()

        kind.apply("ann", listOf(CloudDoc("BBB222", uploaded.fields)))

        val row = row("BBB222")
        assertEquals("zip/6", row.variant)
        assertEquals(42_000L, row.authorTimeMs)
        assertEquals(listOf("AAA111"), kind.pending("ann").map { it.id })
    }

    @Test
    fun `a row restored from a legacy doc takes the variant and author time a later doc carries`() = runTest {
        kind.apply("ann", listOf(doc("AAA111", variant = null, authorTimeMs = null)))

        kind.apply("ann", listOf(doc("AAA111")))

        val row = row("AAA111")
        assertEquals("zip/6", row.variant)
        assertEquals(42_000L, row.authorTimeMs)
        assertTrue(kind.pending("ann").isEmpty())
    }

    @Test
    fun `a local variant and author time win over the doc's`() = runTest {
        seed("AAA111", owner = "ann", fingerprint = "old")

        kind.apply("ann", listOf(doc("AAA111", variant = "zip/8", authorTimeMs = 9_000)))

        val row = row("AAA111")
        assertEquals("zip/6", row.variant)
        assertEquals(42_000L, row.authorTimeMs)
    }

    @Test
    fun `a doc whose variant or author time is mistyped or negative is skipped`() = runTest {
        val restored = kind.apply(
            "ann",
            listOf(
                CloudDoc("AAA111", encoded("AAA111") + ("variant" to 6L)),
                CloudDoc("BBB222", encoded("BBB222") + ("authorTimeMs" to "slow")),
                CloudDoc("CCC333", encoded("CCC333") + ("authorTimeMs" to -1L)),
            ),
        )

        assertEquals(0, restored)
        assertTrue(dao.owned("ann").isEmpty())
    }

    @Test
    fun `the format version is the shared const`() = runTest {
        seed("AAA111", owner = "ann")

        assertEquals(CLOUD_FORMAT_VERSION, kind.pending("ann").single().fields["formatVersion"])
    }

    @Test
    fun `acknowledge marks an unchanged row uploaded`() = runTest {
        seed("AAA111", owner = "ann")
        val uploaded = kind.pending("ann")

        kind.acknowledge("ann", uploaded)

        assertTrue(kind.pending("ann").isEmpty())
        assertNotNull(row("AAA111").uploadedFingerprint)
    }

    @Test
    fun `acknowledge leaves a row edited during the upload pending`() = runTest {
        seed("AAA111", title = "Before", owner = "ann")
        val uploaded = kind.pending("ann")
        dao.upsert(entity("AAA111", title = "After", owner = "ann"))

        kind.acknowledge("ann", uploaded)

        assertEquals(listOf("AAA111"), kind.pending("ann").map { it.id })
    }

    @Test
    fun `acknowledge never marks another account's row`() = runTest {
        seed("AAA111", owner = "bob")

        kind.acknowledge("ann", listOf(CloudDoc("AAA111", encoded("AAA111"))))

        assertNull(row("AAA111").uploadedFingerprint)
    }

    @Test
    fun `a solve count change is not a pending change`() = runTest {
        seed("AAA111", owner = "ann")
        kind.acknowledge("ann", kind.pending("ann"))

        dao.updateSolves("AAA111", 12, 1_000)

        assertTrue(kind.pending("ann").isEmpty())
    }

    @Test
    fun `adopt claims the ownerless rows only`() = runTest {
        seed("OWN111", owner = "ann")
        seed("BOB222", owner = "bob")
        seed("FREE33", owner = null)

        kind.adopt("ann")

        assertEquals("ann", row("FREE33").ownerUid)
        assertEquals("bob", row("BOB222").ownerUid)
        assertEquals("ann", row("OWN111").ownerUid)
    }

    @Test
    fun `forget makes every owned row pending again`() = runTest {
        seed("AAA111", owner = "ann")
        seed("BBB222", owner = "bob")
        kind.acknowledge("ann", kind.pending("ann"))
        kind.acknowledge("bob", kind.pending("bob"))

        kind.forget("ann")

        assertEquals(listOf("AAA111"), kind.pending("ann").map { it.id })
        assertTrue(kind.pending("bob").isEmpty())
    }

    @Test
    fun `pending ids follow the rows not yet acknowledged`() = runTest {
        kind.pendingIds("ann").test {
            assertEquals(emptySet<String>(), awaitItem())

            seed("AAA111", owner = "ann")
            assertEquals(setOf("AAA111"), awaitItem())

            kind.acknowledge("ann", kind.pending("ann"))
            assertEquals(emptySet<String>(), awaitItem())

            dao.updateSolves("AAA111", 2, 5)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun row(code: String): PublishedPuzzleEntity = checkNotNull(dao.find(code)) { "no row $code" }

    private suspend fun seed(
        code: String,
        title: String = "T $code",
        gameId: String = "zip",
        createdAt: Long = 1,
        unpublished: Boolean = false,
        owner: String?,
        fingerprint: String? = null,
    ) = dao.upsert(entity(code, title, gameId, createdAt, unpublished, owner, fingerprint))

    private fun entity(
        code: String,
        title: String = "T $code",
        gameId: String = "zip",
        createdAt: Long = 1,
        unpublished: Boolean = false,
        owner: String?,
        fingerprint: String? = null,
    ) = PublishedPuzzleEntity(
        code = code,
        gameId = gameId,
        variant = "$gameId/6",
        title = title,
        authorTimeMs = 42_000,
        createdAt = createdAt,
        unpublished = unpublished,
        solvesCount = null,
        solvesCountAt = null,
        authorHints = 1,
        ownerUid = owner,
        uploadedFingerprint = fingerprint,
    )

    /** The doc [seed]'s rows encode to; a null [variant] or [authorTimeMs] is a legacy doc without the key. */
    private fun encoded(
        code: String,
        title: String = "T $code",
        gameId: String = "zip",
        createdAt: Long = 1,
        unpublished: Boolean = false,
        variant: String? = "zip/6",
        authorTimeMs: Long? = 42_000,
    ): Map<String, Any?> {
        val fields = mutableMapOf<String, Any?>(
            "formatVersion" to 1L,
            "gameId" to gameId,
            "name" to title,
            "publishedAtMs" to createdAt,
            "unpublished" to unpublished,
        )
        variant?.let { fields["variant"] = it }
        authorTimeMs?.let { fields["authorTimeMs"] = it }
        return fields
    }

    private fun doc(
        code: String,
        name: String = "T $code",
        at: Long = 1,
        unpublished: Boolean = false,
        gameId: String = "zip",
        variant: String? = "zip/6",
        authorTimeMs: Long? = 42_000,
    ) = CloudDoc(code, encoded(code, name, gameId, at, unpublished, variant, authorTimeMs), updatedAtMs = 1_000)

    private fun copyWithoutDetails(row: PublishedPuzzleEntity) = PublishedPuzzleEntity(
        code = row.code,
        gameId = row.gameId,
        variant = "",
        title = row.title,
        authorTimeMs = 0,
        createdAt = row.createdAt,
        unpublished = row.unpublished,
        solvesCount = null,
        solvesCountAt = null,
        authorHints = 0,
        ownerUid = row.ownerUid,
        uploadedFingerprint = null,
    )
}
