package com.ivandsky.puzzlebox.app.e2e

import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.play.ZipPlayFixture
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
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
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Making puzzles inside Games (N9, N10) over the real shell with CustomInGames on: the Custom
 * pill's draft and published rows and the two Make-one doors each open their screen on the Games
 * stack, and Back or the Studio's Delete returns to the screen the door was on. One flag-off row
 * pins that none of those doors shows.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
// A real phone's height: the Studio's board slot needs room for its grid.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class CustomInGamesE2ETest {

    private val drafts = FakeDraftRepository()
    private val published = FakePublishedPuzzleRepository()
    private val stores = module {
        single<DraftRepository> { drafts }
        single<PublishedPuzzleRepository> { published }
    }

    @Test
    fun `a draft row under Custom opens its Studio full-window on Games and Back returns to Custom`() =
        shellRobot(graph = shellGraph(ReleaseFlag.CustomInGames, overrides = stores)) {
            seedDraft()
            tapTab(PlayKey)
            tap(CUSTOM_PILL)

            tap(DRAFT_ROW)

            assertRoute(EDITOR)
            assertState(games(EditorKey(DRAFT_ID)))

            back()
            assertState(games())
            assertRoute(LIBRARY)
            awaitTag(CUSTOM_LIST)
        }

    @Test
    fun `a published row under Custom opens its page on Games with the bar and Back returns to Custom`() =
        shellRobot(graph = shellGraph(ReleaseFlag.CustomInGames, overrides = stores)) {
            seedPublished()
            tapTab(PlayKey)
            tap(CUSTOM_PILL)

            tap(PUBLISHED_ROW)

            assertRoute(PUBLISHED_PAGE)
            // assertState also checks that the bar shows with Games selected.
            assertState(games(PublishedPuzzleKey(CODE.value)))

            back()
            assertState(games())
            assertRoute(LIBRARY)
            awaitTag(CUSTOM_LIST)
        }

    @Test
    fun `Make one on a game page starts a default-size draft in the Studio and Back returns to the page`() =
        shellRobot(graph = shellGraph(ReleaseFlag.CustomInGames, overrides = stores)) {
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
    fun `Make one like this on a result starts a draft of the played size and Back returns to the result`() =
        shellRobot(graph = shellGraph(ReleaseFlag.CustomInGames, overrides = stores)) {
            val played = ZipPlayFixture.envelope().variant
            assertNotEquals("the played size is not the default", ZipEditor.defaultVariant, played)
            tapTab(PlayKey)
            openGame(ZIP)
            solveTodaysZip()
            val result = state.currentStack.last()
            assertTrue("the solve opened $result", result is ResultKey)
            awaitActionable(RESULT_MAKE_ONE)

            tap(RESULT_MAKE_ONE)

            assertRoute(EDITOR)
            val draft = drafts.rows.values.single()
            assertEquals(ZipDefinition.id, draft.gameId)
            assertEquals(played, draft.variant)
            assertState(games(result, EditorKey(draft.id)))

            back()
            assertState(games(result))
            assertRoute(RESULT)
        }

    @Test
    fun `Delete in a Studio opened from a game page returns to the page and removes the draft`() =
        shellRobot(graph = shellGraph(ReleaseFlag.CustomInGames, overrides = stores)) {
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
    fun `with the flag off Games has no Custom pill and a game page no Make one`() =
        shellRobot(graph = shellGraph(overrides = stores)) {
            tapTab(PlayKey)
            awaitTag(DAILIES_PILL)
            assertFalse("Custom pill", shows(CUSTOM_PILL))

            tap(ZIP_ROW)
            awaitTag(ZIP_PAGE)
            awaitTag(PAGE_PLAY)
            assertFalse("Make one", shows(PAGE_MAKE_ONE))
        }

    private fun ShellRobot.openZipPage() {
        tapTab(PlayKey)
        tap(ZIP_ROW)
        awaitTag(ZIP_PAGE)
    }

    /** The three-tab state selected on Games, whose stack is the library then [pushed]. */
    private fun games(vararg pushed: NavKey) = NavigationState(
        tab = PlayKey,
        stacks = listOf(listOf(HomeKey), listOf(PlayKey) + pushed, listOf(HistoryRootKey)),
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
        const val EDITOR = "route.editor"
        const val RESULT = "route.result"
        const val PUBLISHED_PAGE = "route.publishedPuzzle"
        const val DAILIES_PILL = "library.tab.dailies"
        const val CUSTOM_PILL = "library.tab.custom"
        const val CUSTOM_LIST = "custom.list"
        const val DRAFT_ROW = "create.draft.row"
        const val PUBLISHED_ROW = "create.published.row"
        const val ZIP_ROW = "library.row.zip"
        const val ZIP_PAGE = "library.page.zip"
        const val PAGE_PLAY = "library.play"
        const val PAGE_MAKE_ONE = "library.page.makeOne"
        const val RESULT_MAKE_ONE = "result.makeOne"
        const val STUDIO_MORE = "create.studio.more"
        const val STUDIO_DELETE = "create.studio.delete"
    }
}
