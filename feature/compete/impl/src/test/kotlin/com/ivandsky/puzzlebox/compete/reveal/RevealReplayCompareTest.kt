package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.LegBasis
import com.ivandsky.puzzlebox.domain.LegVerdict
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.replay.Lead
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.gameui.DuelSideState
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.TutorialStep
import com.ivandsky.puzzlebox.gameui.replay.GameReplayBoard
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ReplayCompareKey
import com.ivandsky.puzzlebox.play.api.ReplayKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What a duel leg's Compare action is gated on and what it opens: the destination itself is tested where it lives. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RevealReplayCompareTest {

    @get:Rule
    val rule = createComposeRule()

    private val zip = GameId("zip")
    private val queens = GameId("queens")
    private val withBoard = GamePlayRegistry(listOf(PlayUi(zip, board = true), PlayUi(queens, board = false)))
    private val gameOf = { leg: Int -> if (leg == 2) queens else zip }

    @Test
    fun noLegIsOfferedBeforeTheRevealRan() {
        assertEquals(emptySet<Int>(), offeredCompares(emptySet(), gameOf, withBoard))
    }

    @Test
    fun aLegTheViewModelOffersAndWhoseGameDrawsAReplayBoardIsOffered() {
        assertEquals(setOf(0, 1), offeredCompares(setOf(0, 1), gameOf, withBoard))
    }

    @Test
    fun aLegDroppedFromTheViewModelStaysDropped() {
        assertEquals(setOf(1), offeredCompares(setOf(1), gameOf, withBoard))
    }

    @Test
    fun aGameWithoutAReplayBoardIsNotOffered() {
        assertEquals(setOf(0), offeredCompares(setOf(0, 2), gameOf, withBoard))
    }

    @Test
    fun aLegOfNoKnownGameIsNotOffered() {
        assertEquals(emptySet<Int>(), offeredCompares(setOf(0), { null }, withBoard))
    }

    @Test
    fun theKeyNamesBothLegsTheOpponentAndTheRevealsLead() {
        val key = compareKey("K7Q2MX", zip, LegCompare(1, mine = null, theirs = null, opponent = "Sam", finalLead = Lead.Theirs))

        assertEquals(
            ReplayCompareKey.of(
                mine = ReplayKey.leg("zip", "K7Q2MX", 1),
                theirs = ReplayKey.leg("zip", "K7Q2MX", 1, theirs = true),
                myName = "You",
                theirName = "Sam",
                lead = ReplayCompareKey.LEAD_THEIRS,
            ),
            key,
        )
    }

    @Test
    fun everyLeadMapsToItsOwn() {
        val leads = Lead.entries.map { compareKey("K7Q2MX", zip, LegCompare(0, null, null, "Sam", it)).lead }

        assertEquals(listOf(ReplayCompareKey.LEAD_MINE, ReplayCompareKey.LEAD_THEIRS, ReplayCompareKey.LEAD_LEVEL), leads)
    }

    @Test
    fun aRowKeepsItsPlaceInTheMatchWhenAnEarlierPuzzleIsStillPending() {
        val won = LegReveal(zip, DuelSideState.Time(1_000, 0), DuelSideState.Time(2_000, 0), LegVerdict.Win(LegBasis.Time))
        val cards = listOf(0, 2).map { RevealCard(won, "Zip #$it", "#$it", RankOrder.TimeThenHints, "", "", it) }

        assertEquals(listOf(0, 2), puzzleRows(cards).map { it.legIndex })
    }

    @Test
    fun aRowOffersCompareOnlyForAnOfferedLegAndTapsItsLeg() {
        val opened = mutableListOf<Int>()
        rule.setContent {
            PuzzleBoxTheme {
                RevealSummary(
                    summary = SUMMARY,
                    brands = withBoard,
                    wide = false,
                    onBack = {},
                    onRematch = {},
                    onWatchAgain = {},
                    compares = setOf(2),
                    onCompare = { opened += it },
                )
            }
        }

        rule.onAllNodesWithTag("reveal.compare.0").assertCountEquals(0)
        rule.onNodeWithTag("reveal.compare.2").performScrollTo().performClick()

        assertEquals(listOf(2), opened)
    }

    @Test
    fun noCompareIsOfferedWhenNoLegAsksForOne() {
        rule.setContent {
            PuzzleBoxTheme { RevealSummary(SUMMARY, withBoard, wide = false, onBack = {}, onRematch = {}, onWatchAgain = {}) }
        }

        rule.onAllNodesWithTag("reveal.compare.0").assertCountEquals(0)
        rule.onAllNodesWithTag("reveal.compare.2").assertCountEquals(0)
    }

    @Test
    fun aLoadedCompareIsPushedAsItsDestinationThenDropped() {
        val navigator = Navigator(NavigationState(PlayKey, listOf(listOf(PlayKey))))
        var dropped = 0
        rule.setContent {
            OpenCompareEffect(
                compare = LegCompare(0, mine = null, theirs = null, opponent = "Sam", finalLead = Lead.Mine),
                code = "K7Q2MX",
                gameOf = { zip },
                navigator = navigator,
                onOpened = { dropped++ },
            )
        }
        rule.waitForIdle()

        val top = navigator.state.currentStack.last()
        assertTrue("pushed $top", top is ReplayCompareKey && top.theirName == "Sam")
        assertEquals(1, dropped)
    }

    private class PlayUi(override val gameId: GameId, board: Boolean) : GamePlayUi {
        override val brand: GameBrand = GameBrand.Neutral
        override val tutorial: List<TutorialStep> = emptyList()
        override val replayBoard: GameReplayBoard? = if (board) NoBoard else null

        override fun createController(env: PlayEnvironment): GamePlayController = throw UnsupportedOperationException("brand only")

        @Composable
        override fun Board(controller: GamePlayController, modifier: Modifier) = Unit

        @Composable
        override fun Screen(
            controller: GamePlayController,
            status: PlayBoardStatus,
            layout: PlayLayout,
            sidePanel: (@Composable ColumnScope.() -> Unit)?,
            onRetry: () -> Unit,
            onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
            modifier: Modifier,
        ) = Unit
    }

    private object NoBoard : GameReplayBoard {
        override val pieces = "pieces"

        @Composable
        override fun Board(frame: ReplayFrame, modifier: Modifier) = Unit
    }

    private companion object {
        private fun row(leg: Int) = PuzzleRow(
            gameId = GameId("zip"),
            title = "Zip #${400 + leg}",
            basis = "14 s faster",
            mine = "0:58",
            theirs = "1:12",
            winner = Side.Mine,
            tied = false,
            margin = RowMargin(1f, "14 s"),
            legIndex = leg,
        )

        /** Legs 0 and 2 settled; leg 1 is still pending, so the second row is leg 2. */
        val SUMMARY = DuelSummary(
            me = "Ivan",
            opponent = "Sam",
            kind = VerdictKind.Win,
            myScore = 2,
            theirScore = 0,
            bestOf = "Best of 3 · Mon 9 Nov",
            tiles = SummaryTiles(puzzlesWon = 2, puzzles = 2, fastestMs = 58_000, totalMs = 116_000),
            rows = listOf(row(0), row(2)),
            shareText = "",
        )
    }
}
