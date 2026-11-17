package com.ivandsky.puzzlebox.create.published

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.create.GOLDEN_PHONE
import com.ivandsky.puzzlebox.create.showGolden
import com.ivandsky.puzzlebox.gameui.PlayLayout
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
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
 * Ticket 128: the author's own published page draws the plain, sharp board of the kept draft for each of the five
 * games, with no Studio board, so no verdict mark and no tool. The ticket, Duplicate, Unpublish and the game's
 * disabled publish fields stay.
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

    @Test fun `draws Zip's plain board and no Studio board`() = plainBoardFor(PlainGame.ZIP)
    @Test fun `draws Tango's plain board and no Studio board`() = plainBoardFor(PlainGame.TANGO)
    @Test fun `draws Queens' plain board and no Studio board`() = plainBoardFor(PlainGame.QUEENS)
    @Test fun `draws Mini Sudoku's plain board and no Studio board`() = plainBoardFor(PlainGame.SUDOKU)
    @Test fun `draws Strands' plain board and no Studio board`() = plainBoardFor(PlainGame.STRANDS)

    @Test fun `shows no verdict to the Studio board`() {
        val world = show(PlainGame.QUEENS)
        awaitCount(PLAIN_CONTENT, 1)
        assertTrue(world.studioVerdicts.isEmpty())
    }

    @Test fun `keeps the ticket, the actions and the disabled publish fields`() {
        show(PlainGame.STRANDS)
        awaitCount(PLAIN_CONTENT, 1)
        assertCount("create.published.ticket", 1)
        assertCount("create.publishedPage.duplicate", 1)
        assertCount("create.publishedPage.unpublish", 1)
        assertCount("create.publishedPage.back", 1)
        assertCount("fake.editor.publishFields.disabled", 1)
    }

    @Test fun `still shows the unpublished record`() {
        show(PlainGame.ZIP, unpublished = true)
        awaitCount(PLAIN_CONTENT, 1)
        awaitCount("create.published.unpublished", 1)
        assertCount(STUDIO_BOARD, 0)
    }

    @Test fun `a game that draws no plain board shows the page without a board`() {
        show(PlainGame.SUDOKU, withReplayBoard = false)
        awaitCount("create.published.ticket", 1)
        rule.waitUntil(TIMEOUT_MS) { settled() }
        assertCount(PLAIN_CONTENT, 0)
        assertCount(PLAIN_LOADING, 0)
        assertCount(STUDIO_BOARD, 0)
        assertCount("create.publishedPage.duplicate", 1)
    }

    private fun plainBoardFor(game: PlainGame) {
        val world = show(game)
        awaitCount(PLAIN_CONTENT, 1)
        assertCount(PLAIN_BOARD, 1)
        assertCount(STUDIO_BOARD, 0)
        assertCount("create.studio.board.readOnly", 0)
        assertEquals("${game.label} draws its own puzzle's givens", listOf(game.id), world.givensAsked.map { it.gameId })
    }

    private fun show(game: PlainGame, unpublished: Boolean = false, withReplayBoard: Boolean = true): PublishedPlainWorld {
        val world = PublishedPlainWorld(game, withReplayBoard)
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
