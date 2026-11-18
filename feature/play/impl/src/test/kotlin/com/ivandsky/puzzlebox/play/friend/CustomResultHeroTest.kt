package com.ivandsky.puzzlebox.play.friend

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.AuthorPin
import com.ivandsky.puzzlebox.domain.CustomBoardView
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.RecordVerdict
import com.ivandsky.puzzlebox.gameui.SamePuzzleSolves
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.game.VariantId
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A community result as the daily's hero: its facts (one title, "by <author>", Hints 0, my rank, the author's time, Practice
 * on a play again, no compare on a first solve) and its page (hero buttons, the ranks list in the side card on a tablet and under
 * the hero on a phone, the author row that does not rank, my own row marked).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CustomResultHeroTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val author = AuthorPin(uid = "uid-author", name = "Jonas", timeMs = 102_000L, hints = 0)
    private val ranked = CustomBoardView.Ranked(
        top = listOf(previewBoardRow(1, "Maja", 74_000L), previewBoardRow(2, "Me", 81_000L, isMe = true)),
        mine = null,
        author = author,
        count = 8,
    )

    private fun puzzle() = ReceivedPuzzle(
        code = checkNotNull(ShareCode.parse("K7Q2MX")),
        gameId = GameId("queens"),
        variant = VariantId("queens/n8"),
        sharedFormatVersion = 1,
        puzzleBytes = ByteArray(0),
        title = "Crown the castle",
        authorName = " Jonas ",
        note = "",
        authorTimeMs = 102_000L,
        authorHints = 0,
        authorUid = "uid-author",
        openedAt = Instant.EPOCH,
        unpublished = false,
        postState = PostState.Pending,
        postAttempts = 0,
        difficulty = null,
    )

    private fun state(standing: CustomStanding? = CustomStanding.Record, board: CustomBoardView? = ranked, puzzle: ReceivedPuzzle? = puzzle()) = CustomResultState(
        timeMs = 81_000L,
        hintsUsed = 0,
        title = "Queens 8×8 · Crown the castle",
        brand = GameBrand(AccentSlot.Violet, GameGlyphs.Queens),
        rankOrder = RankOrder.TimeThenHints,
        puzzle = puzzle,
        standing = standing,
        board = board,
        heroPolish = true,
        samePuzzle = SamePuzzleSolves.None.copy(columns = true),
    )

    @Test
    fun `facts name the author once, show Hints 0, my rank of the board and the author's time`() {
        val facts = state().heroFacts()

        assertEquals("by Jonas", facts.dateLine)
        assertEquals("Queens 8×8 · Crown the castle", facts.game)
        val chips = facts.community!!.chips
        assertEquals(listOf("#2", "1:42"), chips.map { it.value })
        assertEquals(listOf("of 8", "Author"), chips.map { it.label })
    }

    @Test
    fun `my rank chip is left out until the board lists me, and the author chip until the puzzle is read`() {
        assertEquals(emptyList<String>(), state(board = null, puzzle = null).heroFacts().community!!.chips.map { it.tag })
        assertEquals("", state(puzzle = null).heroFacts().dateLine)
    }

    @Test
    fun `a play again names the standing record as the previous best and is not a new best`() {
        val facts = state(standing = CustomStanding.PlayAgain(BestScore(70_000L, 0))).heroFacts()

        assertTrue(facts.playAgain)
        assertEquals(RecordVerdict.PlayAgain, facts.verdict)
        assertEquals(BestScore(70_000L, 0), facts.previousBest)
    }

    @Test
    fun `a first solve is a new best with no previous best`() {
        val facts = state().heroFacts()

        assertEquals(RecordVerdict.NewBest, facts.verdict)
        assertNull(facts.previousBest)
    }

    private var done = 0
    private var shared = 0
    private var again = 0

    private fun setContent(widthDp: Int, heightDp: Int, state: CustomResultState = state()) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                CustomResultHeroScreen(
                    state = state,
                    onDone = { done++ },
                    onShare = { shared++ },
                    onPlayAgain = { again++ },
                    onSignIn = {},
                    modifier = Modifier.requiredSize(widthDp.dp, heightDp.dp),
                )
            }
        }
    }

    @Test
    fun `the page has the hero, its chips and buttons, and the ranks list with the author row and my row`() {
        setContent(1280, 800)

        composeRule.onNodeWithTag("result.hero.title").assertTextContains("Crown the castle", substring = true)
        composeRule.onNodeWithTag("result.hero.date").assertTextContains("by Jonas")
        composeRule.onNodeWithTag("result.hero.chip.hints").assertExists()
        composeRule.onNodeWithTag("result.hero.chip.rank").assertExists()
        composeRule.onNodeWithTag("result.hero.chip.author").assertExists()
        composeRule.onNodeWithTag("custom_board").assertExists()
        composeRule.onNodeWithTag("custom_board.author").assertExists()
        composeRule.onNode(hasText("doesn't rank", substring = true)).assertExists()
        composeRule.onNodeWithTag("custom_board.row.me").assertExists()
        composeRule.onNodeWithTag("result.share").performClick()
        composeRule.onNodeWithTag("result.playAgain").performClick()
        composeRule.onNodeWithTag("result.done").performClick()
        assertEquals(listOf(1, 1, 1), listOf(shared, again, done))
    }

    @Test
    fun `tablet puts the ranks list right of the hero and phone puts it under`() {
        setContent(1280, 800)
        val hero = composeRule.onNodeWithTag("result.hero.card").getUnclippedBoundsInRoot()
        val board = composeRule.onNodeWithTag("custom_board").getUnclippedBoundsInRoot()
        assertTrue("tablet: board ${board.left} right of hero ${hero.right}", board.left >= hero.right)
    }

    @Test
    fun `phone puts the ranks list under the hero`() {
        setContent(411, 891)
        val hero = composeRule.onNodeWithTag("result.hero.card").getUnclippedBoundsInRoot()
        val board = composeRule.onNodeWithTag("custom_board").getUnclippedBoundsInRoot()
        assertTrue("phone: board ${board.top} under hero ${hero.bottom}", board.top >= hero.bottom)
    }
}
