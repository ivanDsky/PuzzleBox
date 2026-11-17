package com.ivandsky.puzzlebox.create.published

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.create.GOLDEN_PHONE
import com.ivandsky.puzzlebox.create.showGolden
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * Ticket 128: the author's own published page. With CommunityPuzzles on it draws the plain, sharp board of the kept
 * draft for each of the five games, with no Studio board, so no verdict mark and no tool; with it off (or no flags)
 * it is the Studio's board with its verdict, as shipped. The ticket, Duplicate, Unpublish and the game's disabled
 * publish fields stay either way.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class PublishedPlainBoardTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test fun `flag on draws Zip's plain board and no Studio board`() = plainBoardFor(PlainGame.ZIP)
    @Test fun `flag on draws Tango's plain board and no Studio board`() = plainBoardFor(PlainGame.TANGO)
    @Test fun `flag on draws Queens' plain board and no Studio board`() = plainBoardFor(PlainGame.QUEENS)
    @Test fun `flag on draws Mini Sudoku's plain board and no Studio board`() = plainBoardFor(PlainGame.SUDOKU)
    @Test fun `flag on draws Strands' plain board and no Studio board`() = plainBoardFor(PlainGame.STRANDS)

    @Test fun `flag on shows no verdict to the Studio board`() {
        val world = show(PlainGame.QUEENS, FakeReleaseFlags(ReleaseFlag.CommunityPuzzles))
        awaitCount(PLAIN_CONTENT, 1)
        assertTrue(world.studioVerdicts.isEmpty())
    }

    @Test fun `flag on keeps the ticket, the actions and the disabled publish fields`() {
        show(PlainGame.STRANDS, FakeReleaseFlags(ReleaseFlag.CommunityPuzzles))
        awaitCount(PLAIN_CONTENT, 1)
        assertCount("create.published.ticket", 1)
        assertCount("create.publishedPage.duplicate", 1)
        assertCount("create.publishedPage.unpublish", 1)
        assertCount("create.publishedPage.back", 1)
        assertCount("fake.editor.publishFields.disabled", 1)
    }

    @Test fun `flag on still shows the unpublished record`() {
        show(PlainGame.ZIP, FakeReleaseFlags(ReleaseFlag.CommunityPuzzles), unpublished = true)
        awaitCount(PLAIN_CONTENT, 1)
        awaitCount("create.published.unpublished", 1)
        assertCount(STUDIO_BOARD, 0)
    }

    @Test fun `flag on holds the plain board as plainBoard`() {
        val world = show(PlainGame.TANGO, FakeReleaseFlags(ReleaseFlag.CommunityPuzzles))
        awaitCount(PLAIN_CONTENT, 1)
        val ready = world.viewModel?.state?.value as PublishedPuzzleViewModel.State.Ready
        assertTrue(ready.plainBoard)
    }

    @Test fun `flag on with a game that draws no plain board shows the page without a board`() {
        show(PlainGame.SUDOKU, FakeReleaseFlags(ReleaseFlag.CommunityPuzzles), withReplayBoard = false)
        awaitCount("create.published.ticket", 1)
        rule.waitUntil(TIMEOUT_MS) { settled() }
        assertCount(PLAIN_CONTENT, 0)
        assertCount(PLAIN_LOADING, 0)
        assertCount(STUDIO_BOARD, 0)
        assertCount("create.publishedPage.duplicate", 1)
    }

    @Test fun `flag off is Zip's shipped Studio board with its verdict`() = studioBoardFor(PlainGame.ZIP)
    @Test fun `flag off is Tango's shipped Studio board with its verdict`() = studioBoardFor(PlainGame.TANGO)
    @Test fun `flag off is Queens' shipped Studio board with its verdict`() = studioBoardFor(PlainGame.QUEENS)
    @Test fun `flag off is Mini Sudoku's shipped Studio board with its verdict`() = studioBoardFor(PlainGame.SUDOKU)
    @Test fun `flag off is Strands' shipped Studio board with its verdict`() = studioBoardFor(PlainGame.STRANDS)

    @Test fun `no flags at all is the shipped Studio board`() {
        val world = show(PlainGame.QUEENS, flags = null)
        awaitCount(STUDIO_BOARD, 1)
        assertCount(PLAIN_BOARD, 0)
        assertTrue(world.givensAsked.isEmpty())
    }

    private fun plainBoardFor(game: PlainGame) {
        val world = show(game, FakeReleaseFlags(ReleaseFlag.CommunityPuzzles))
        awaitCount(PLAIN_CONTENT, 1)
        assertCount(PLAIN_BOARD, 1)
        assertCount(STUDIO_BOARD, 0)
        assertCount("create.studio.board.readOnly", 0)
        assertEquals("${game.label} draws its own puzzle's givens", listOf(game.id), world.givensAsked.map { it.gameId })
    }

    private fun studioBoardFor(game: PlainGame) {
        val world = show(game, FakeReleaseFlags())
        awaitCount(STUDIO_BOARD, 1)
        assertCount(PLAIN_BOARD, 0)
        assertCount(PLAIN_CONTENT, 0)
        assertCount("create.studio.board.readOnly", 1)
        assertTrue(world.givensAsked.isEmpty())
        assertFalse((world.viewModel?.state?.value as PublishedPuzzleViewModel.State.Ready).plainBoard)
    }

    private fun show(game: PlainGame, flags: ReleaseFlags?, unpublished: Boolean = false, withReplayBoard: Boolean = true): PublishedPlainWorld {
        val world = PublishedPlainWorld(game, flags, withReplayBoard)
        world.start(unpublished)
        rule.showGolden(dark = false, frozen = false) {
            PublishedPuzzleRoute(world.key, PlayLayout.SinglePane, onBack = {}, onOpen = {})
        }
        return world
    }

    private fun count(tag: String) = rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

    private fun assertCount(tag: String, expected: Int) = assertEquals(tag, expected, count(tag))

    private fun awaitCount(tag: String, expected: Int) = rule.waitUntil(TIMEOUT_MS) {
        ShadowLooper.idleMainLooper(FRAME_MS, TimeUnit.MILLISECONDS)
        count(tag) == expected
    }

    private fun settled(): Boolean {
        ShadowLooper.idleMainLooper(SETTLE_MS, TimeUnit.MILLISECONDS)
        return true
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val FRAME_MS = 16L
        const val SETTLE_MS = 1_000L
    }
}
