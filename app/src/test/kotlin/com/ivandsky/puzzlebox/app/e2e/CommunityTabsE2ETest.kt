package com.ivandsky.puzzlebox.app.e2e

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.queens.editor.QueensEditor
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
 * The phone's Community over the real shell with CustomInGames and CommunityPuzzles on (M12-4): three tabs under the
 * head, the filter ⋮ on New today only, and Yours holding create's own list, whose draft opens its Studio and comes
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
        shellRobot(graph = shellGraph(ReleaseFlag.CustomInGames, ReleaseFlag.CommunityPuzzles, overrides = stores)) {
            runBlocking {
                drafts.upsert(readyDraft(EditorDraft.fresh(QueensEditor, QueensEditor.defaultVariant), "Castle").copy(id = "castle", testSolve = null))
            }
            tapTab(CommunityKey)
            assertRoute(COMMUNITY)
            awaitTag(TAB_NEW)
            awaitTag(TAB_FRIENDS)
            assertTrue("the filter on New today", shows(FILTER))
            assertFalse("a draft outside Yours", shows(DRAFT_ROW))

            tap(TAB_YOURS)
            awaitTag(DRAFT_ROW)
            assertFalse("the filter on Yours", shows(FILTER))

            tap(DRAFT_ROW)
            assertRoute(EDITOR)

            back()
            assertRoute(COMMUNITY)
            awaitTag(DRAFT_ROW)
            assertFalse("the filter on Yours", shows(FILTER))
        }

    private companion object {
        const val COMMUNITY = "route.community"
        const val EDITOR = "route.editor"
        const val TAB_NEW = "community.tab.new"
        const val TAB_FRIENDS = "community.tab.friends"
        const val TAB_YOURS = "community.tab.yours"
        const val FILTER = "community.filter.menu"
        const val DRAFT_ROW = "create.draft.row"
    }
}
