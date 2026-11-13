package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.app.e2e.CloudSyncWorld.Device
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.PublishedPuzzle
import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.data.repository.PublishedPuzzleRepository
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.editor.ZipDraft
import com.ivandsky.puzzlebox.games.zip.editor.ZipEditor
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A published puzzle on a second phone (ticket 45): A keeps a published board as a draft under its code, B restores it,
 * and B's Published page opens it, with A's variant and author time, while B's Drafts tab lists only the ordinary draft.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
// A real phone's height: the read-only board's slot needs room for its grid.
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class CloudSyncPublishedRestoreE2ETest {

    private val world = CloudSyncWorld()
    private val ada = Identity(uid = "uid-ada", displayName = "Ada", photoUrl = null)
    private val phoneA = world.device("phone-a")
    private val phoneB = world.device("phone-b")

    @After
    fun closeWorld() = world.close()

    @Test
    fun `a published puzzle restored on B opens its page and its board is not listed as a draft`() {
        val keptBoard = EditorDraft(ZipEditor, DraftState(BOARD)).encode()
        var onA: PublishedPuzzle? = null
        phoneA.shell {
            phoneA.signIn(ada)
            phoneA.publish(keptBoard)
            phoneA.makeDraft("plain", "Plain")
            awaitCloud("A's rows in the cloud") { phoneA.uploaded(ada.uid) && phoneA.synced() && world.space.docs(ada.uid, "drafts").size == 2 }
            onA = phoneA.published()
        }
        val published = checkNotNull(onA)
        assertEquals("A's variant is the board's", ZipEditor.variantOf(BOARD), published.variant)
        assertEquals("A's author time", AUTHOR_TIME_MS, published.authorTimeMs)
        assertEquals("the cloud holds the kept draft's code", CODE, world.space.docs(ada.uid, "drafts").mapNotNull { it.fields["reservedCode"] }.single())

        phoneB.shell(CreateKey) {
            phoneB.signIn(ada)
            awaitCloud("B's restore") { phoneB.synced() && phoneB.shownDrafts().size == 2 && phoneB.published() != null }

            val restored = checkNotNull(phoneB.published())
            assertEquals("B's variant matches A's", published.variant, restored.variant)
            assertEquals("B's author time matches A's", published.authorTimeMs, restored.authorTimeMs)
            assertEquals("the kept draft came with its code", CODE, phoneB.shownDrafts().single { it.id == KEPT }.reservedCode)
            assertTrue("the kept draft's board is A's", keptBoard.contentEquals(phoneB.shownDrafts().single { it.id == KEPT }.blob))

            // The Drafts tab lists the ordinary draft only; the kept one is the published puzzle's board.
            awaitTag("create.draft.row")
            rule.waitForIdle()
            assertEquals("one draft on the Drafts tab", 1, rule.onAllNodesWithTag("create.draft.row").fetchSemanticsNodes().size)

            tap("create.tab.published")
            tap("create.published.row")
            awaitTag("route.publishedPuzzle")
            awaitTag("create.studio.board.readOnly")
            awaitTag("create.published.authorTime")
            rule.onNodeWithTag("create.published.authorTime").assertTextContains(formatDuration(AUTHOR_TIME_MS), substring = true)
        }
    }

    // -- the scenario's vocabulary ---------------------------------------------------------------------------------

    private fun Device.published(): PublishedPuzzle? = runBlocking {
        graph.koin.get<PublishedPuzzleRepository>().observeAll().first().singleOrNull()
    }

    /** What publishing leaves behind: the board kept as a draft under the code, and the listing row. */
    private fun Device.publish(board: ByteArray) {
        val editor = checkNotNull(graph.koin.get<GameRegistry>()[ZipDefinition.id]?.editor)
        val code = checkNotNull(ShareCode.parse(CODE))
        runBlocking {
            graph.koin.get<DraftRepository>().upsert(
                Draft(
                    id = KEPT,
                    gameId = ZipDefinition.id,
                    variant = ZipEditor.variantOf(BOARD),
                    draftFormatVersion = editor.draftFormatVersion,
                    blob = board,
                    title = TITLE,
                    updatedAt = Instant.parse("2026-11-01T10:00:00Z"),
                    testSolve = TestSolve(EditorDraft(ZipEditor, DraftState(BOARD)).layoutKey, AUTHOR_TIME_MS, 0),
                    reservedCode = CODE,
                    imported = false,
                    authorName = "",
                    note = "",
                ),
            )
            graph.koin.get<PublishedPuzzleRepository>().upsert(
                PublishedPuzzle(
                    code = code,
                    gameId = ZipDefinition.id,
                    variant = ZipEditor.variantOf(BOARD),
                    title = TITLE,
                    authorTimeMs = AUTHOR_TIME_MS,
                    authorHints = 0,
                    createdAt = Instant.parse("2026-11-01T10:05:00Z"),
                    unpublished = false,
                    solvesCount = null,
                    solvesCountAt = null,
                ),
            )
        }
    }

    private companion object {
        const val KEPT = "corridor"
        const val CODE = "PKA001"
        const val TITLE = "Corridor"
        const val AUTHOR_TIME_MS = 102_000L
        const val SIZE = 5

        /** The forced 5×5 corridor board's one path, row-major cell indices. */
        val SOLUTION = listOf(0, 1, 2, 3, 4, 9, 8, 7, 6, 5, 10, 11, 12, 13, 14, 19, 18, 17, 16, 15, 20, 21, 22, 23, 24)

        /** Walls under these cells force [SOLUTION]; the waypoints number its ends and middle. */
        val WALLED = listOf(0, 1, 2, 3, 6, 7, 8, 9, 10, 11, 12, 13, 16, 17, 18, 19)

        val BOARD = ZipDraft(
            n = SIZE,
            walls = WALLED.map { Wall(Cell(it), WallDirection.DOWN) }.toSet(),
            path = SOLUTION,
            waypoints = listOf(0, 12, 24),
        )
    }
}
