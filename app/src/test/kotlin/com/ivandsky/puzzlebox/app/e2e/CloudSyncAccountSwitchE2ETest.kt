package com.ivandsky.puzzlebox.app.e2e

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Two accounts on one phone never mix (A3, A4, SC4, SC5), over the real flag-on graph in [CloudSyncWorld]. A finishes
 * games, makes drafts and saves a board; signs out; B signs in: not one of A's rows reaches B's space, none is shown to
 * B, and B's first finish of A's puzzle is B's own record. When A is back, A's rows are back. A uid that flips under an
 * upload commits nothing under the new uid.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class)
class CloudSyncAccountSwitchE2ETest {

    private val world = CloudSyncWorld(linkedIn = true)
    private val a = Identity(uid = "uid-a", displayName = "Ada", photoUrl = null)
    private val b = Identity(uid = "uid-b", displayName = "Bea", photoUrl = null)
    private val yesterday = E2E_TODAY.minusDays(1)

    @After
    fun closeWorld() {
        world.close()
    }

    @Test
    fun `two accounts on one phone never mix and A's rows come back with A`() {
        val phone = world.device()
        phone.shell {
            // A finishes today's Zip for real, and the rest of A's data goes in through the same repositories.
            phone.signIn(a)
            openGame("zip")
            solveTodaysZip()
            back() // the result is immersive: the library is back
            awaitCloud("A's finish stored") { phone.shownRecords().isNotEmpty() }
            val aToday = phone.shownRecords().single()
            val aRef = phone.finish(yesterday, puzzleNumber = 590)
            phone.makeDraft("draft-1", "Ada's first")
            phone.makeDraft("draft-2", "Ada's second")
            phone.saveBoard(yesterday)

            awaitCloud("A's rows in A's space") {
                listOf("completions", "drafts", "boards").all { world.space.docs(a.uid, it).isNotEmpty() } && phone.uploaded(a.uid) && phone.synced()
            }
            val aDocs = spaceOf(a.uid)
            assertEquals("both finishes are shown to A", 2, phone.shownRecords().size)
            assertEquals(setOf("draft-1", "draft-2"), phone.shownDrafts().map { it.id }.toSet())
            assertEquals(setOf(yesterday), phone.shownBoardDays(yesterday))

            // A signs out, B signs in.
            phone.signOut()
            val writesBefore = world.space.writes
            val readsBefore = world.space.reads
            phone.signIn(b)
            awaitCloud("B's first run") { phone.synced() && world.space.reads > readsBefore && phone.uploaded(b.uid) }

            assertEquals("not one write after B signed in", writesBefore, world.space.writes)
            assertTrue("no doc was put under B", world.space.payloads.none { it.uid == b.uid })
            assertTrue("B's space is empty", world.space.isEmpty(b.uid))
            assertEquals("A's space is as A left it", aDocs, spaceOf(a.uid))
            assertEquals("none of A's finishes is shown to B", emptyList<Any>(), phone.shownRecords())
            assertEquals("none of A's drafts is shown to B", emptyList<Any>(), phone.shownDrafts())
            assertEquals("none of A's boards is shown to B", emptySet<Any>(), phone.shownBoardDays(yesterday))

            // B finishes the puzzle A finished, slower than A did: B's first finish is B's record, and A's time is not
            // B's best. (A's finished play is still in this process's session store, so B's finish goes in directly.)
            val shared = phone.finish(E2E_TODAY, puzzleNumber = null, timeMs = 90_000)
            assertEquals("the puzzle A finished by hand", aToday.puzzleRef, shared)
            val own = phone.shownRecords().single()
            assertEquals(shared, own.puzzleRef)
            assertEquals("B's own time, not A's", 90_000L, own.time)
            assertTrue("B's first finish of the puzzle is B's record", own.isRecord)
            assertEquals("A's yesterday is not B's", false, phone.shownRecords().any { it.puzzleRef == aRef })
            awaitCloud("B's finish in B's space") {
                world.space.docs(b.uid, "completions").isNotEmpty() && phone.uploaded(b.uid) && phone.synced()
            }
            assertEquals("B's rows went to B's space only", aDocs, spaceOf(a.uid))
            assertFalse("only B's finish is in B's space", world.space.docs(b.uid, "completions").isEmpty())
            assertEquals("B's space holds no draft and no board", emptyList<Any>(), world.space.docs(b.uid, "drafts") + world.space.docs(b.uid, "boards"))

            // A signs back in: A's rows return, B's stay B's.
            phone.signOut()
            val aReads = world.space.reads
            phone.signIn(a)
            awaitCloud("A's run after coming back") { phone.synced() && world.space.reads > aReads }
            assertEquals("A's finishes are back", 2, phone.shownRecords().size)
            assertEquals("A's own time on the shared puzzle", aToday.time, phone.shownRecords().single { it.puzzleRef == aToday.puzzleRef }.time)
            assertTrue("A's yesterday is back", phone.shownRecords().any { it.puzzleRef == aRef && it.isRecord })
            assertEquals("A's drafts are back", setOf("draft-1", "draft-2"), phone.shownDrafts().map { it.id }.toSet())
            assertEquals("A's board is back", setOf(yesterday), phone.shownBoardDays(yesterday))
            assertEquals("A's space is still as A left it", aDocs, spaceOf(a.uid))
        }
    }

    @Test
    fun `a uid that flips during an upload commits nothing under the new uid`() {
        val phone = world.device()
        phone.shell {
            // A draft made with nobody signed in belongs to nobody: the first account to sign in claims and uploads it.
            phone.makeDraft("draft-1", "Made offline")
            val hold = world.gate.holdNextPut()
            phone.signIn(a)
            awaitCloud("A's upload in flight") { hold.arrived.isCompleted }

            // The account changes under the held upload; the upload then reaches the cloud as A's.
            phone.signIn(b)
            hold.release.complete(Unit)
            awaitCloud("B's run after the flip") { phone.synced() }

            assertTrue("nothing reached A's space after A was gone", world.space.isEmpty(a.uid))
            assertTrue("nothing was committed under B", world.space.isEmpty(b.uid))
            assertTrue("no doc was put under B", world.space.payloads.none { it.uid == b.uid })
            assertEquals("the draft is A's, so B does not see it", emptyList<Any>(), phone.shownDrafts())
            assertFalse("the draft is not B's to upload", phone.uploaded(a.uid))

            // A returns: the draft was never acknowledged, so it goes up now.
            phone.signOut()
            phone.signIn(a)
            awaitCloud("A's draft in A's space") { world.space.docs(a.uid, "drafts").isNotEmpty() && phone.uploaded(a.uid) }
            assertEquals(setOf("draft-1"), phone.shownDrafts().map { it.id }.toSet())
            assertTrue("B's space stays empty", world.space.isEmpty(b.uid))
        }
    }

    /** A space's docs by kind, as (id, updatedAtMs): a draft's blob is a byte array, which does not compare. */
    private fun spaceOf(uid: String): Map<String, List<Pair<String, Long?>>> =
        kinds.associateWith { kind -> world.space.docs(uid, kind).map { it.id to it.updatedAtMs } }

    private companion object {
        val kinds = listOf("completions", "drafts", "boards")
    }
}
