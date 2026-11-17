package com.ivandsky.puzzlebox.compete.duel

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.designsystem.MaxContentWidth
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.testing.game.TestGames
import java.time.Clock
import java.time.ZoneOffset
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import com.ivandsky.puzzlebox.model.GameId

private val SUDOKU = GameId("Mini Sudoku")

/**
 * A duel's page in the tablet's two columns, whoever the opponent: a self duel keeps what it has on the phone, the
 * results card with See results and Remove duel and the DONE / IN THIS PUZZLE card, all inside the centred, capped page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class DuelPaneFeaturesTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val sudoku = LegRef.Daily(SUDOKU, LocalDate.of(2026, 11, 9), 371)
    private val refs = List(3) { LegRef.Daily(TestGames.alpha, LocalDate.of(2026, 11, 9), 580 + it) }
    private var revealed = 0
    private var removed = 0

    @Before
    fun startKoinWithTheTestGames() {
        startKoin {
            modules(
                module {
                    single { TestGames.registry(listOf(TestGames.alpha, SUDOKU)) }
                    single { GamePlayRegistry(emptyList()) }
                },
            )
        }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    private fun show(state: DuelUiState.Participant, boards: LegBoards = NoLegBoards) {
        rule.setContent {
            PuzzleBoxTheme {
                DuelLegs(
                    state = state,
                    layout = PlayLayout.TwoPane,
                    now = DuelPageFixtures.NOW,
                    time = CompeteTime(Clock.fixed(DuelPageFixtures.NOW, ZoneOffset.UTC), Locale.US),
                    actions = LegsActions({}, {}, { revealed++ }, { removed++ }),
                    modifier = Modifier.fillMaxSize(),
                    boards = boards,
                )
            }
        }
        rule.waitForIdle()
    }

    private fun inLeft(tag: String) = hasTestTag(tag) and hasAnyAncestor(hasTestTag("duel.legs.grid.left"))

    private fun inCallout(text: String) = hasText(text) and hasAnyAncestor(hasTestTag("duel.legs.callout"))

    private fun inRight(tag: String) = hasTestTag(tag) and hasAnyAncestor(hasTestTag("duel.legs.grid.right"))

    @Test
    fun `a self duel played in full has the results card, See results and Remove duel under the list`() {
        show(DuelPageFixtures.self(refs, doneLegs = setOf(0, 1, 2)))

        rule.onNode(inLeft("duel.legs.footer")).performScrollTo().assertIsDisplayed()
        rule.onNode(inLeft("duel.legs.reveal")).assertIsDisplayed()
        rule.onNode(inLeft("duel.legs.remove")).assertIsDisplayed()
        val list = rule.onNodeWithTag("duel.legs.card.2").getUnclippedBoundsInRoot()
        val footer = rule.onNodeWithTag("duel.legs.footer").getUnclippedBoundsInRoot()
        val remove = rule.onNodeWithTag("duel.legs.remove").getUnclippedBoundsInRoot()
        assertTrue("the results card follows the list", footer.top >= list.bottom)
        assertTrue("Remove duel is under the results card", remove.top >= footer.bottom)

        rule.onNode(inLeft("duel.legs.reveal")).performClick()
        rule.onNode(inLeft("duel.legs.remove")).performClick()
        assertEquals(1, revealed)
        assertEquals(1, removed)
    }

    @Test
    fun `the DONE card is in the detail column of a played puzzle`() {
        show(DuelPageFixtures.self(refs, doneLegs = setOf(0, 1, 2)))

        rule.onNode(inRight("duel.legs.callout")).assertIsDisplayed()
        rule.onNode(inCallout("DONE")).assertIsDisplayed()
        rule.onNode(inCallout("You played this one. Past you’s time shows in the results.")).assertIsDisplayed()
        rule.onAllNodesWithTag("duel.legs.action.0").assertCountEquals(0)
    }

    @Test
    fun `a self duel with a puzzle to play shows the steps and Start, no results card, and still Remove duel`() {
        show(DuelPageFixtures.self(refs, doneLegs = setOf(0)))

        rule.onNode(inCallout("IN THIS PUZZLE")).assertIsDisplayed()
        rule.onNode(inCallout("Hints count against you.")).assertIsDisplayed()
        rule.onNode(inRight("duel.legs.action.1")).assertIsDisplayed()
        rule.onAllNodesWithTag("duel.legs.reveal").assertCountEquals(0)
        rule.onNode(inLeft("duel.legs.remove")).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a self duel is the capped centred page, not two panes edge to edge`() {
        show(DuelPageFixtures.self(refs, doneLegs = setOf(0, 1, 2)))

        val root = rule.onRoot().getUnclippedBoundsInRoot()
        val grid = rule.onNodeWithTag("duel.legs.grid").getUnclippedBoundsInRoot()
        assertTrue("capped: ${grid.width}", grid.width <= MaxContentWidth + 32.dp + 1.dp)
        assertEquals("centred across", (root.left + root.right).value / 2f, (grid.left + grid.right).value / 2f, 1f)
    }

    @Test
    fun `an open duel keeps its footer-free list and no self-only controls`() {
        show(DuelPageFixtures.open(refs, doneLegs = setOf(0)))

        rule.onNode(inRight("duel.legs.action.1")).assertIsDisplayed()
        rule.onAllNodesWithTag("duel.legs.remove").assertCountEquals(0)
        rule.onAllNodesWithTag("duel.legs.callout").assertCountEquals(0)
    }

    private val givens = StubGivens()
    private val board = LegBoard.Of(givens.givens(com.ivandsky.puzzlebox.game.Puzzle(TestGames.alpha, com.ivandsky.puzzlebox.game.VariantId("v"), 1, byteArrayOf())))

    @Test
    fun `the selected puzzle's board is blurred above its details while the puzzle is not solved`() {
        show(DuelPageFixtures.open(refs, doneLegs = setOf(0)), { board })

        rule.onNode(inRight("duel.legs.board.hidden")).assertIsDisplayed()
        rule.onAllNodesWithTag("duel.legs.board.sharp").assertCountEquals(0)
        val board = rule.onNodeWithTag("duel.legs.board.hidden").getUnclippedBoundsInRoot()
        val detail = rule.onNodeWithTag("duel.legs.detail").getUnclippedBoundsInRoot()
        val right = rule.onNodeWithTag("duel.legs.grid.right").getUnclippedBoundsInRoot()
        assertTrue("above the details", board.bottom <= detail.top)
        assertTrue("within the right column", board.left >= right.left && board.right <= right.right)
        assertTrue("at most 400 dp high: ${board.height}", board.height <= 400.dp + 1.dp)
    }

    @Test
    fun `a solved puzzle's board shows sharp, and picking an unplayed one blurs it again`() {
        show(DuelPageFixtures.open(refs, doneLegs = setOf(0)), { board })
        rule.onNodeWithTag("duel.legs.card.0").performClick()
        rule.waitForIdle()

        rule.onNode(inRight("duel.legs.board.sharp")).assertIsDisplayed()
        rule.onAllNodesWithTag("duel.legs.board.hidden").assertCountEquals(0)

        rule.onNodeWithTag("duel.legs.card.1").performClick()
        rule.waitForIdle()

        rule.onNode(inRight("duel.legs.board.hidden")).assertIsDisplayed()
        rule.onAllNodesWithTag("duel.legs.board.sharp").assertCountEquals(0)
    }

    @Test
    fun `while the puzzle is read the board's place holds the quiet bars, then the board takes it`() {
        val answer = kotlinx.coroutines.CompletableDeferred<LegBoard>()
        show(DuelPageFixtures.self(refs, doneLegs = setOf(0)), { answer.await() })

        rule.onNode(inRight("duel.legs.board.loading")).assertIsDisplayed()
        rule.onAllNodesWithTag("duel.legs.board.hidden").assertCountEquals(0)

        answer.complete(board)
        rule.waitForIdle()

        rule.onAllNodesWithTag("duel.legs.board.loading").assertCountEquals(0)
        rule.onNode(inRight("duel.legs.board.hidden")).assertIsDisplayed()
    }

    @Test
    fun `a puzzle with no board leaves the details alone`() {
        show(DuelPageFixtures.self(refs, doneLegs = setOf(0, 1, 2)), { LegBoard.None })

        rule.onAllNodesWithTag("duel.legs.board.hidden").assertCountEquals(0)
        rule.onAllNodesWithTag("duel.legs.board.sharp").assertCountEquals(0)
        rule.onAllNodesWithTag("duel.legs.board.loading").assertCountEquals(0)
        rule.onNode(inRight("duel.legs.detail")).assertIsDisplayed()
    }

    @Test
    fun `Start is on screen at 1280 by 800 with the board shrunk to the room the details leave`() {
        show(DuelPageFixtures.self(refs, doneLegs = setOf(0)), { board })

        rule.onNode(inRight("duel.legs.action.1")).assertIsDisplayed()
        val root = rule.onRoot().getUnclippedBoundsInRoot()
        val start = rule.onNode(inRight("duel.legs.action.1")).getUnclippedBoundsInRoot()
        val shown = rule.onNode(inRight("duel.legs.board.hidden")).getUnclippedBoundsInRoot()
        assertTrue("Start's bottom ${start.bottom} is within the pane ${root.bottom}", start.bottom <= root.bottom)
        assertTrue("the board keeps at least 160 dp: ${shown.height}", shown.height >= 160.dp - 1.dp)
        assertTrue("and is no larger than the friend page's: ${shown.height}", shown.height <= 400.dp + 1.dp)
    }

    @Test
    fun `a leg title wraps beside the wide status chip instead of ellipsizing`() {
        show(DuelPageFixtures.self(listOf(refs[0], sudoku, refs[2]), doneLegs = setOf(0)))

        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        rule.onNode(hasText("Mini Sudoku #371") and hasAnyAncestor(hasTestTag("duel.legs.card.1")), useUnmergedTree = true)
            .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult]
            .action?.invoke(results)
        val layout = results.single()
        assertEquals("the title may take two lines before it is cut", 2, layout.layoutInput.maxLines)
        assertEquals("the whole title is drawn", "Mini Sudoku #371", layout.layoutInput.text.text)
    }
}
