package com.ivandsky.puzzlebox.testing.contract

import com.ivandsky.puzzlebox.data.cloud.CloudDoc
import com.ivandsky.puzzlebox.data.cloud.CloudResult
import com.ivandsky.puzzlebox.data.cloud.CloudSpace
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Behavior every [CloudSpace] adapter must pass. Subclasses supply a fresh, empty, online [space] and the two hooks
 * below; adapter-specific cases live in the subclass. Every observation goes through the port itself.
 * Test names are camel case because this class ships in a main source set that gets dexed.
 */
abstract class CloudSpaceContract {

    protected abstract val space: CloudSpace

    /** Makes [uid] the signed-in account for the calls that follow. */
    protected abstract fun signInAs(uid: String)

    /** The server clock: the `updatedAtMs` stamped on every doc written from now on. */
    protected abstract fun setNow(nowMs: Long)

    @Before
    fun startTheSession() {
        signInAs(ANN)
        setNow(T0)
    }

    private fun doc(id: String, vararg fields: Pair<String, Any?>) = CloudDoc(id, mapOf(*fields))

    private suspend fun read(uid: String = ANN, kind: String = DRAFTS, cursorMs: Long? = null): List<CloudDoc> {
        val result = space.changedSince(uid, kind, cursorMs)
        return (result as CloudResult.Ok).value
    }

    private suspend fun ids(kind: String, uid: String = ANN): Set<String> = read(uid, kind).map { it.id }.toSet()

    @Test
    fun anEmptySpaceReturnsNothing() = runTest {
        assertEquals(CloudResult.Ok(emptyList<CloudDoc>()), space.changedSince(ANN, DRAFTS, null))
    }

    @Test
    fun putThenChangedSinceRoundTripsTheDocWithTheServerStamp() = runTest {
        assertEquals(CloudResult.Ok(Unit), space.put(ANN, DRAFTS, listOf(doc("d1", "title" to "Corner", "size" to 5L, "ok" to true))))

        assertEquals(listOf(CloudDoc("d1", mapOf("title" to "Corner", "size" to 5L, "ok" to true), T0)), read())
    }

    @Test
    fun theCallersUpdatedAtIsIgnored() = runTest {
        space.put(ANN, DRAFTS, listOf(CloudDoc("d1", mapOf("n" to 1L), updatedAtMs = 42L)))

        assertEquals(T0, read().single().updatedAtMs)
    }

    @Test
    fun everyPlainValueTypeRoundTrips() = runTest {
        val fields = mapOf(
            "text" to "a",
            "long" to 1L,
            "flag" to false,
            "double" to 1.5,
            "bytes" to byteArrayOf(1, 2, 3),
            "none" to null,
            "list" to listOf("x", 2L, listOf(true)),
            "map" to mapOf("inner" to mapOf("deep" to 3L)),
        )
        space.put(ANN, DRAFTS, listOf(CloudDoc("d1", fields)))

        val back = read().single().fields
        assertEquals(fields.keys, back.keys)
        assertArrayEquals(byteArrayOf(1, 2, 3), back["bytes"] as ByteArray)
        assertEquals(fields - "bytes", back - "bytes")
    }

    @Test
    fun putMergesNestedMapKeysAndReplacesEverythingElse() = runTest {
        space.put(
            ANN, DRAFTS,
            listOf(
                doc(
                    "d1",
                    "records" to mapOf("a" to mapOf("t" to 1L, "h" to 0L), "b" to mapOf("t" to 2L)),
                    "tags" to listOf("x", "y"),
                    "title" to "old",
                    "kept" to 7L,
                ),
            ),
        )
        space.put(
            ANN, DRAFTS,
            listOf(doc("d1", "records" to mapOf("a" to mapOf("t" to 9L), "c" to mapOf("t" to 3L)), "tags" to listOf("z"), "title" to "new")),
        )

        val fields = read().single().fields
        assertEquals(
            mapOf("a" to mapOf("t" to 9L, "h" to 0L), "b" to mapOf("t" to 2L), "c" to mapOf("t" to 3L)),
            fields["records"],
        )
        assertEquals(listOf("z"), fields["tags"])
        assertEquals("new", fields["title"])
        assertEquals(7L, fields["kept"])
    }

    @Test
    fun aValueThatIsNotAMapOnEitherSideReplacesInsteadOfMerging() = runTest {
        space.put(ANN, DRAFTS, listOf(doc("d1", "a" to mapOf("k" to 1L), "b" to "text")))
        space.put(ANN, DRAFTS, listOf(doc("d1", "a" to "flat", "b" to mapOf("k" to 2L))))

        assertEquals(mapOf("a" to "flat", "b" to mapOf("k" to 2L)), read().single().fields)
    }

    @Test
    fun anOverwrittenDocGetsTheNewStamp() = runTest {
        space.put(ANN, DRAFTS, listOf(doc("d1", "n" to 1L)))
        setNow(T0 + 500)
        space.put(ANN, DRAFTS, listOf(doc("d1", "n" to 2L)))

        assertEquals(listOf(CloudDoc("d1", mapOf("n" to 2L), T0 + 500)), read())
    }

    @Test
    fun changedSinceIncludesTheCursorInstantAndNothingBefore() = runTest {
        space.put(ANN, DRAFTS, listOf(doc("early", "n" to 1L)))
        setNow(T0 + 100)
        space.put(ANN, DRAFTS, listOf(doc("on", "n" to 2L)))
        setNow(T0 + 200)
        space.put(ANN, DRAFTS, listOf(doc("late", "n" to 3L)))

        assertEquals(setOf("early", "on", "late"), read(cursorMs = null).map { it.id }.toSet())
        assertEquals(setOf("early", "on", "late"), read(cursorMs = T0).map { it.id }.toSet())
        assertEquals(setOf("on", "late"), read(cursorMs = T0 + 100).map { it.id }.toSet())
        assertEquals(setOf("late"), read(cursorMs = T0 + 101).map { it.id }.toSet())
        assertEquals(emptySet<String>(), read(cursorMs = T0 + 201).map { it.id }.toSet())
    }

    @Test
    fun kindsAreSeparateSpaces() = runTest {
        space.put(ANN, DRAFTS, listOf(doc("same", "n" to 1L)))
        space.put(ANN, PUBLISHED, listOf(doc("same", "n" to 2L)))

        assertEquals(1L, read(kind = DRAFTS).single().fields["n"])
        assertEquals(2L, read(kind = PUBLISHED).single().fields["n"])
    }

    @Test
    fun deleteRemovesOnlyTheNamedDocsAndAnAbsentIdIsNotAnError() = runTest {
        space.put(ANN, DRAFTS, listOf(doc("d1", "n" to 1L), doc("d2", "n" to 2L), doc("d3", "n" to 3L)))

        assertEquals(CloudResult.Ok(Unit), space.delete(ANN, DRAFTS, listOf("d1", "d3", "never-existed")))

        assertEquals(setOf("d2"), ids(DRAFTS))
    }

    @Test
    fun deleteAllRemovesExactlyThePassedKinds() = runTest {
        space.put(ANN, DRAFTS, listOf(doc("a", "n" to 1L)))
        space.put(ANN, PUBLISHED, listOf(doc("b", "n" to 1L)))
        space.put(ANN, RECEIVED, listOf(doc("c", "n" to 1L)))

        assertEquals(CloudResult.Ok(Unit), space.deleteAll(ANN, listOf(DRAFTS, PUBLISHED)))

        assertEquals(emptySet<String>(), ids(DRAFTS))
        assertEquals(emptySet<String>(), ids(PUBLISHED))
        assertEquals(setOf("c"), ids(RECEIVED))
    }

    @Test
    fun deleteAllLeavesAnotherAccountsDocsAlone() = runTest {
        space.put(ANN, DRAFTS, listOf(doc("mine", "n" to 1L)))
        signInAs(BOB)
        space.put(BOB, DRAFTS, listOf(doc("his", "n" to 1L)))

        space.deleteAll(BOB, listOf(DRAFTS))

        assertEquals(emptySet<String>(), ids(DRAFTS, BOB))
        signInAs(ANN)
        assertEquals(setOf("mine"), ids(DRAFTS, ANN))
    }

    @Test
    fun accountsHaveSeparateSpaces() = runTest {
        space.put(ANN, DRAFTS, listOf(doc("shared-id", "owner" to "ann")))
        signInAs(BOB)
        space.put(BOB, DRAFTS, listOf(doc("shared-id", "owner" to "bob")))

        assertEquals("bob", read(BOB).single().fields["owner"])
        signInAs(ANN)
        assertEquals("ann", read(ANN).single().fields["owner"])
    }

    @Test
    fun writesForADifferentAccountThanTheSignedInOneReturnWrongAccountAndWriteNothing() = runTest {
        space.put(ANN, DRAFTS, listOf(doc("keep", "n" to 1L)))
        space.put(ANN, PUBLISHED, listOf(doc("keep", "n" to 1L)))
        signInAs(BOB)

        assertEquals(CloudResult.WrongAccount, space.put(ANN, DRAFTS, listOf(doc("new", "n" to 2L), doc("keep", "n" to 9L))))
        assertEquals(CloudResult.WrongAccount, space.delete(ANN, DRAFTS, listOf("keep")))
        assertEquals(CloudResult.WrongAccount, space.deleteAll(ANN, listOf(DRAFTS, PUBLISHED)))

        signInAs(ANN)
        assertEquals(setOf("keep"), ids(DRAFTS))
        assertEquals(listOf(CloudDoc("keep", mapOf("n" to 1L), T0)), read())
        assertEquals(setOf("keep"), ids(PUBLISHED))
    }

    private companion object {
        const val ANN = "uid-ann"
        const val BOB = "uid-bob"
        const val DRAFTS = "drafts"
        const val PUBLISHED = "published"
        const val RECEIVED = "received"
        const val T0 = 1_700_000_000_000L
    }
}
