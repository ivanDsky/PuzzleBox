package com.ivandsky.puzzlebox.app.e2e

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.play.ZipPlayFixture
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zipui.ZipPlayController
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.play.PlaySessionStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Criterion 5, the unfinished game never syncs, over the real flag-on graph in [CloudSyncWorld]. On A a Zip is played
 * partway and left unfinished, and the sync runs with a finished game and a draft beside it: no payload in the cloud's
 * write log carries the unfinished game, and a second phone that signs in restores finished games and no play state.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class)
class CloudSyncUnfinishedGameE2ETest {

    private val world = CloudSyncWorld()
    private val ada = Identity(uid = "uid-ada", displayName = "Ada", photoUrl = null)
    private val phoneA = world.device("phone-a")
    private val phoneB = world.device("phone-b")
    private val today = PlaySlot.Daily(ZipDefinition.id, E2E_TODAY)
    private val yesterday = E2E_TODAY.minusDays(1)

    @After
    fun closeWorld() = world.close()

    @Test
    fun `a game left unfinished on A is in no payload and B restores no play state`() {
        phoneA.shell {
            openGame("zip")
            val store = graph.koin.get<PlaySessionStore>()
            fun zip() = checkNotNull(rule.runOnIdle { store.peek(today) }?.controller as? ZipPlayController) { "no live zip play" }
            rule.waitUntil(5_000L) { rule.runOnIdle { store.peek(today) }?.let { (it.controller as ZipPlayController).session } != null }
            // Three cells of a five-by-five path: well short of the solution.
            rule.runOnIdle { ZipPlayFixture.solution.take(3).forEach { zip().onDragEvent(DragEvent.Extend(GridCell(it.idx / 5, it.idx % 5))) } }
            background()
            awaitCloud("the unfinished game saved on A") { today in phoneA.graph.saved.rows }
            foreground()
            assertTrue("today's game is unfinished on A", phoneA.shownRecords().none { (it.puzzleRef.key as PuzzleKey.Daily).date == E2E_TODAY })

            // The sync has something to send beside it.
            phoneA.finish(yesterday, puzzleNumber = 590)
            phoneA.makeDraft("draft-1", "Ada's draft")
            phoneA.signIn(ada)
            awaitCloud("A's sync") {
                world.space.docs(ada.uid, "completions").isNotEmpty() && world.space.docs(ada.uid, "drafts").isNotEmpty() &&
                    phoneA.uploaded(ada.uid) && phoneA.synced()
            }

            assertTrue("the unfinished game is still on A", today in phoneA.graph.saved.rows)
            assertTrue("something was written to search", world.space.payloads.isNotEmpty())
            val blob = checkNotNull(phoneA.graph.saved.rows[today])
            world.space.payloads.forEach { written ->
                val text = flatten(written.doc.id) + flatten(written.doc.fields)
                assertFalse("a ${written.kind} payload names today's unfinished game: ${written.doc.id}", text.any { E2E_TODAY.toString() in it })
                assertFalse("a ${written.kind} payload carries the saved play", text.any { blob.isNotEmpty() && it.contains(blob.toString(Charsets.ISO_8859_1)) })
            }
        }

        phoneB.shell {
            phoneB.signIn(ada)
            awaitCloud("B's restore") { phoneB.synced() && phoneB.shownRecords().isNotEmpty() }

            assertEquals("B restores the finished game and no other", listOf(yesterday), phoneB.shownRecords().map { (it.puzzleRef.key as PuzzleKey.Daily).date })
            assertTrue("B restores no play state", phoneB.graph.saved.rows.isEmpty())
        }
    }

    /** Every key and every value of [value] as text, one entry each; bytes as one char per byte. */
    private fun flatten(value: Any?): List<String> = when (value) {
        null -> emptyList()
        is ByteArray -> listOf(value.toString(Charsets.ISO_8859_1))
        is Map<*, *> -> value.flatMap { (key, nested) -> flatten(key) + flatten(nested) }
        is List<*> -> value.flatMap(::flatten)
        else -> listOf(value.toString())
    }
}
