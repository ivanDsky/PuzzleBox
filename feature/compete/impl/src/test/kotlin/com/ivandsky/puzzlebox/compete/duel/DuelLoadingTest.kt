package com.ivandsky.puzzlebox.compete.duel

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.SHIMMER_TAG
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.testing.game.TestGames
import java.time.Clock
import java.time.LocalDate
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

/** The duel's loading slot on a 1280×800 tablet: the two-column skeleton on the pane layout, the spinner on the phone's. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class DuelLoadingTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val refs = List(3) { LegRef.Daily(TestGames.alpha, LocalDate.of(2026, 11, 9), 580 + it) }
    private val stub = StubGivens(8, 8)

    @Before
    fun startKoinWithTheTestGame() {
        startKoin {
            modules(
                module {
                    single { TestGames.registry(listOf(TestGames.alpha)) }
                    single { GamePlayRegistry(emptyList()) }
                },
            )
        }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    @Test
    fun `the pane layout loads as two panes edge to edge with a divider between`() {
        rule.setContent { PuzzleBoxTheme { DuelLoading(PlayLayout.TwoPane) } }

        val root = rule.onNodeWithTag("duel.legs.grid.skeleton").getUnclippedBoundsInRoot()
        val left = rule.onNodeWithTag("duel.legs.grid.skeleton.left").getUnclippedBoundsInRoot()
        val right = rule.onNodeWithTag("duel.legs.grid.skeleton.right").getUnclippedBoundsInRoot()
        assertEquals("fills across", 1280f, root.width.value, 1f)
        assertEquals("fills down", 800f, root.height.value, 1f)
        assertEquals("left starts at the edge", 0f, left.left.value, 1f)
        assertEquals("right ends at the edge", 1280f, right.right.value, 1f)
        assertEquals("equal panes", left.width.value, right.width.value, 2f)
    }

    @Test
    fun `the phone layout loads as the spinner, not the skeleton`() {
        rule.setContent { PuzzleBoxTheme { DuelLoading(PlayLayout.SinglePane) } }

        rule.onAllNodesWithTag("duel.legs.grid.skeleton").assertCountEquals(0)
    }

    @Test
    fun `the two-pane skeleton's bars and blocks shimmer`() {
        rule.setContent { PuzzleBoxTheme { DuelLoading(PlayLayout.TwoPane) } }

        assertTrue(rule.onAllNodesWithTag(SHIMMER_TAG, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `the skeleton's board is centred in the right pane, as the loaded board is`() {
        rule.setContent { PuzzleBoxTheme { DuelLoading(PlayLayout.TwoPane) } }

        val pane = rule.onNodeWithTag("duel.legs.grid.skeleton.right").getUnclippedBoundsInRoot()
        val board = rule.onNodeWithTag("duel.legs.grid.skeleton.board").getUnclippedBoundsInRoot()
        assertEquals("centres", (pane.left + pane.right).value / 2f, (board.left + board.right).value / 2f, 1f)
    }

    @Test
    fun `the skeleton's board is exactly where the loaded board lands`() {
        var loaded by mutableStateOf(false)
        rule.setContent {
            PuzzleBoxTheme {
                if (loaded) {
                    DuelLegs(
                        state = DuelPageFixtures.open(refs, emptySet()),
                        layout = PlayLayout.TwoPane,
                        now = DuelPageFixtures.NOW,
                        time = CompeteTime(Clock.fixed(DuelPageFixtures.NOW, ZoneOffset.UTC), Locale.US),
                        actions = LegsActions({}, {}, {}, {}),
                        modifier = Modifier.fillMaxSize(),
                        boards = LegBoards { leg -> LegBoard.Of(stub.givens(Puzzle(leg.gameId, VariantId("v"), 1, byteArrayOf()))) },
                    )
                } else {
                    DuelLoading(PlayLayout.TwoPane)
                }
            }
        }
        val skeleton = rule.onNodeWithTag("duel.legs.grid.skeleton.board").getUnclippedBoundsInRoot()

        loaded = true
        rule.waitForIdle()

        val board = rule.onNodeWithTag("duel.legs.board.hidden").getUnclippedBoundsInRoot()
        assertEquals("left", board.left.value, skeleton.left.value, 1f)
        assertEquals("top", board.top.value, skeleton.top.value, 1f)
        assertEquals("width", board.width.value, skeleton.width.value, 1f)
        assertEquals("height", board.height.value, skeleton.height.value, 1f)
    }
}
