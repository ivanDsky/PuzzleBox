package com.ivandsky.puzzlebox.app.e2e

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.queens.editor.QueensEditor
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.api.CommunityKey
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The phone's Community over the real shell (M12-4): three tabs under the
 * head, the filter chips on New today only, and Yours holding create's own list, whose draft opens its Studio and comes
 * back to Yours.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class CommunityTabsE2ETest {

    private val drafts = FakeDraftRepository()
    private val stores = module { single<DraftRepository> { drafts } }

    @Test
    fun `Community is three tabs and a draft under Yours opens its Studio and returns to Yours`() =
        shellRobot(graph = shellGraph(overrides = stores)) {
            runBlocking {
                drafts.upsert(readyDraft(EditorDraft.fresh(QueensEditor, QueensEditor.defaultVariant), "Castle").copy(id = "castle", testSolve = null))
            }
            tapTab(CommunityKey)
            assertRoute(COMMUNITY)
            awaitTag(TAB_NEW)
            awaitTag(TAB_FRIENDS)
            assertTrue("the filter chips on New today", shows(FILTER))
            assertFalse("a draft outside Yours", shows(DRAFT_ROW))

            tap(TAB_YOURS)
            awaitTag(DRAFT_ROW)
            assertFalse("the filter chips on Yours", shows(FILTER))

            tap(DRAFT_ROW)
            assertRoute(EDITOR)

            back()
            assertRoute(COMMUNITY)
            awaitTag(DRAFT_ROW)
            assertFalse("the filter chips on Yours", shows(FILTER))
        }

    @Test
    fun `New today has filter chips and a game pick narrows the list`() {
        val world = FriendWorld()
        try {
            runBlocking {
                publish(world, QUEENS_CODE, "queens")
                publish(world, ZIP_CODE, "zip")
            }
            shellRobot(graph = world.graph) {
                tapTab(CommunityKey)
                awaitTag(FILTER)
                awaitTag("community.row.$QUEENS_CODE")
                assertTrue("a zip row too", shows("community.row.$ZIP_CODE"))

                tap(FILTER)
                awaitTag("community.filter.game.zip")
                tap("community.filter.game.zip")

                awaitTag("community.row.$ZIP_CODE")
                assertFalse("the queens row is filtered out", shows("community.row.$QUEENS_CODE"))
            }
        } finally {
            world.close()
        }
    }

    private suspend fun publish(world: FriendWorld, code: String, game: String) {
        world.shared.signedInUid = "other"
        world.shared.publishesToday = 0
        world.shared.publish(
            SharedPuzzleDoc(
                code = checkNotNull(ShareCode.parse(code)), formatVersion = SharedPuzzleDoc.FORMAT_VERSION, gameId = game,
                variant = "$game/v1", shareCodecVersion = 1, payload = byteArrayOf(1), title = "Puzzle $code", authorName = "Other",
                note = "", authorUid = "other", authorTimeMs = 1_000L, authorHints = 0, createdAtMs = System.currentTimeMillis(),
                unpublished = false, visibility = SharedPuzzleDoc.Visibility.Public,
            ),
        )
    }

    private companion object {
        const val QUEENS_CODE = "K7Q2MX"
        const val ZIP_CODE = "P4R8NT"
        const val COMMUNITY = "route.community"
        const val EDITOR = "route.editor"
        const val TAB_NEW = "community.tab.new"
        const val TAB_FRIENDS = "community.tab.friends"
        const val TAB_YOURS = "community.tab.yours"
        const val FILTER = "community.filter.game"
        const val DRAFT_ROW = "create.draft.row"
    }
}
