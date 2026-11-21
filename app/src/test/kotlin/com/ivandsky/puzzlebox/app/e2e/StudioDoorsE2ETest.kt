package com.ivandsky.puzzlebox.app.e2e

import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.api.PublishedPuzzleKey
import com.ivandsky.puzzlebox.data.model.PublishedPuzzle
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.data.repository.PublishedPuzzleRepository
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.editor.QueensEditor
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.editor.ZipEditor
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.play.api.CommunityKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Making puzzles (N9, N10) over the real shell: Community's Yours draft and published rows open their
 * screen on the Community stack, the game page's Make one opens the Studio on the Games stack, and Back or the
 * Studio's Delete returns to the screen the door was on. Games itself holds only Dailies and Duels.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
// A real phone's height: the Studio's board slot needs room for its grid.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class StudioDoorsE2ETest {

    private val drafts = FakeDraftRepository()
    private val published = FakePublishedPuzzleRepository()
    private val stores = module {
        single<DraftRepository> { drafts }
        single<PublishedPuzzleRepository> { published }
    }

    @Test
    fun `a draft row under Yours opens its Studio full-window on Community and Back returns to Yours`() =
        shellRobot(graph = shellGraph(overrides = stores)) {
            seedDraft()
            tapTab(CommunityKey)
            tap(YOURS_TAB)

            tap(DRAFT_ROW)

            assertRoute(EDITOR)
            assertState(community(EditorKey(DRAFT_ID)))

            back()
            assertState(community())
            assertRoute(COMMUNITY)
            awaitTag(DRAFT_ROW)
        }

    @Test
    fun `a published row under Yours opens its page on Community with the bar and Back returns to Yours`() =
        shellRobot(graph = shellGraph(overrides = stores)) {
            seedPublished()
            tapTab(CommunityKey)
            tap(YOURS_TAB)

            tap(PUBLISHED_ROW)

            assertRoute(PUBLISHED_PAGE)
            // assertState also checks that the bar shows with Community selected.
            assertState(community(PublishedPuzzleKey(CODE.value)))

            back()
            assertState(community())
            assertRoute(COMMUNITY)
            awaitTag(PUBLISHED_ROW)
        }

    @Test
    fun `Make one on a game page starts a default-size draft in the Studio and Back returns to the page`() =
        shellRobot(graph = shellGraph(overrides = stores)) {
            openZipPage()

            tap(PAGE_MAKE_ONE)

            assertRoute(EDITOR)
            val draft = drafts.rows.values.single()
            assertEquals(ZipDefinition.id, draft.gameId)
            assertEquals(ZipEditor.defaultVariant, draft.variant)
            assertState(games(EditorKey(draft.id)))

            back()
            assertState(games())
            assertRoute(LIBRARY)
            awaitTag(ZIP_PAGE)
        }

    @Test
    fun `a result has no Make one like this`() =
        shellRobot(graph = shellGraph(overrides = stores)) {
            tapTab(PlayKey)
            openGame(ZIP)
            solveTodaysZip()
            val result = state.currentStack.last()
            assertTrue("the solve opened $result", result is ResultKey)
            assertRoute(RESULT)
            awaitTag(RESULT_TIME)

            assertFalse("Make one like this", shows(RESULT_MAKE_ONE))
        }

    @Test
    fun `Delete in a Studio opened from a game page returns to the page and removes the draft`() =
        shellRobot(graph = shellGraph(overrides = stores)) {
            openZipPage()
            tap(PAGE_MAKE_ONE)
            assertRoute(EDITOR)

            tap(STUDIO_MORE)
            tap(STUDIO_DELETE)

            assertState(games())
            assertRoute(LIBRARY)
            awaitTag(ZIP_PAGE)
            awaitIdling { drafts.rows.isEmpty() }
        }

    @Test
    fun `Games holds only the Dailies and Duels pills`() =
        shellRobot(graph = shellGraph(overrides = stores)) {
            tapTab(PlayKey)
            awaitTag(DAILIES_PILL)
            assertTrue("Duels pill", shows(DUELS_PILL))
            assertFalse("Custom pill", shows(CUSTOM_PILL))
            assertFalse("Friends pill", shows(FRIENDS_PILL))
        }

    private fun ShellRobot.openZipPage() {
        tapTab(PlayKey)
        tap(ZIP_ROW)
        awaitTag(ZIP_PAGE)
    }

    /** The four-tab state selected on Games, whose stack is the library then [pushed]. */
    private fun games(vararg pushed: NavKey) = tabs(PlayKey, pushed)

    /** The four-tab state selected on Community, whose stack is its root then [pushed]. */
    private fun community(vararg pushed: NavKey) = tabs(CommunityKey, pushed)

    private fun tabs(selected: NavKey, pushed: Array<out NavKey>) = NavigationState(
        tab = selected,
        stacks = listOf(HomeKey, PlayKey, CommunityKey, HistoryRootKey).map { root ->
            if (root == selected) listOf(root) + pushed else listOf(root)
        },
    )

    private fun seedDraft() = runBlocking {
        drafts.upsert(readyDraft(EditorDraft.fresh(QueensEditor, QueensEditor.defaultVariant), "Castle").copy(id = DRAFT_ID, testSolve = null))
    }

    private fun seedPublished() = runBlocking {
        published.upsert(
            PublishedPuzzle(
                code = CODE,
                gameId = QueensDefinition.id,
                variant = QueensEditor.defaultVariant,
                title = "Castle",
                authorTimeMs = 102_000,
                authorHints = 0,
                createdAt = Instant.parse("2026-11-01T10:00:00Z"),
                unpublished = false,
                solvesCount = null,
                solvesCountAt = null,
            ),
        )
    }

    private companion object {
        const val ZIP = "zip"
        const val DRAFT_ID = "castle"
        val CODE: ShareCode = checkNotNull(ShareCode.parse("K7Q2MX"))
        const val LIBRARY = "route.library"
        const val COMMUNITY = "route.community"
        const val EDITOR = "route.editor"
        const val RESULT = "route.result"
        const val PUBLISHED_PAGE = "route.publishedPuzzle"
        const val DAILIES_PILL = "library.tab.dailies"
        const val DUELS_PILL = "library.tab.duels"
        const val CUSTOM_PILL = "library.tab.custom"
        const val FRIENDS_PILL = "library.tab.friends"
        const val YOURS_TAB = "community.tab.yours"
        const val DRAFT_ROW = "create.draft.row"
        const val PUBLISHED_ROW = "create.published.row"
        const val ZIP_ROW = "library.row.zip"
        const val ZIP_PAGE = "library.page.zip"
        const val PAGE_MAKE_ONE = "library.howto.makeOwn"
        const val RESULT_MAKE_ONE = "result.makeOne"
        const val RESULT_TIME = "result.hero.time"
        const val STUDIO_MORE = "create.studio.more"
        const val STUDIO_DELETE = "create.studio.delete"
    }
}
