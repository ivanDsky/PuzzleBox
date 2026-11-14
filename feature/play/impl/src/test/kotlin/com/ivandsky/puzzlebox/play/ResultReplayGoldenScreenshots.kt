package com.ivandsky.puzzlebox.play

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.domain.CustomBoardView
import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.domain.LegVerdict
import com.ivandsky.puzzlebox.gameui.DuelSide
import com.ivandsky.puzzlebox.gameui.DuelSideState
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.RecordVerdict
import com.ivandsky.puzzlebox.gameui.ResultHeroActions
import com.ivandsky.puzzlebox.gameui.ResultHeroFacts
import com.ivandsky.puzzlebox.gameui.ResultHeroScreen
import com.ivandsky.puzzlebox.gameui.ResultPrimaryAction
import com.ivandsky.puzzlebox.gameui.TutorialStep
import com.ivandsky.puzzlebox.gameui.replay.GameReplayBoard
import com.ivandsky.puzzlebox.gameui.replay.ReplaySection
import com.ivandsky.puzzlebox.gameui.replay.ReplaySectionState
import com.ivandsky.puzzlebox.gameui.replay.ReplayTags
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.play.api.ReplayKey
import com.ivandsky.puzzlebox.play.friend.CustomResultScreen
import com.ivandsky.puzzlebox.play.friend.CustomResultState
import com.ivandsky.puzzlebox.play.friend.CustomStanding
import com.ivandsky.puzzlebox.play.friend.PreviewBoardAuthor
import com.ivandsky.puzzlebox.play.friend.UnderUpBar
import com.ivandsky.puzzlebox.play.friend.previewBoardRow
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import java.time.Instant
import com.ivandsky.puzzlebox.gameui.ResultColumnMaxWidth
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The result surfaces that carry a Watch replay row, under their Up bar, phone and tablet, light and dark, with a short
 * (12 moves) and a long (64 pieces, about an hour) replay over the replay fixtures: my own result (row in the hero), a friend's
 * puzzle (row below the card), and a duel leg, solved (row in the hero) and gave up (row below the card). The phone
 * captures show the first screen, except the custom result, whose long board scrolls to its replay row. Goldens in `snapshots/replay-results/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class ResultReplayGoldenScreenshotsPhone : ResultReplayGoldenScreenshots("phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class ResultReplayGoldenScreenshotsTablet : ResultReplayGoldenScreenshots("tablet")

abstract class ResultReplayGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun columnMaxPx() = with(rule.density) { ResultColumnMaxWidth.roundToPx() }

    @Test fun `own short light`() = capture("own-short", dark = false) { OwnResult(SHORT) }
    @Test fun `own short dark`() = capture("own-short", dark = true) { OwnResult(SHORT) }
    @Test fun `own long light`() = capture("own-long", dark = false) { OwnResult(LONG) }
    @Test fun `own long dark`() = capture("own-long", dark = true) { OwnResult(LONG) }

    @Test fun `custom short light`() = capture("custom-short", dark = false, scrollToReplay = true) { CustomResult(SHORT) }
    @Test fun `custom short dark`() = capture("custom-short", dark = true, scrollToReplay = true) { CustomResult(SHORT) }
    @Test fun `custom long light`() = capture("custom-long", dark = false, scrollToReplay = true) { CustomResult(LONG) }
    @Test fun `custom long dark`() = capture("custom-long", dark = true, scrollToReplay = true) { CustomResult(LONG) }

    @Test fun `leg solved short light`() = capture("leg-solved-short", dark = false, scrollToReplay = true) { LegResult(SHORT, gaveUp = false) }
    @Test fun `leg solved short dark`() = capture("leg-solved-short", dark = true, scrollToReplay = true) { LegResult(SHORT, gaveUp = false) }
    @Test fun `leg solved long light`() = capture("leg-solved-long", dark = false, scrollToReplay = true) { LegResult(LONG, gaveUp = false) }
    @Test fun `leg solved long dark`() = capture("leg-solved-long", dark = true, scrollToReplay = true) { LegResult(LONG, gaveUp = false) }

    /** VX-1, VX-2: the result column and the Watch replay row never outgrow the 600 dp result column, so a tablet centres/caps them. */
    @Test fun `own result replay row stays in the result column`() {
        rule.showGolden(dark = false) { UnderUpBar { OwnResult(LONG) } }
        assertTrue(rule.onNodeWithTag(ReplayTags.WATCH_ROW).fetchSemanticsNode().size.width <= columnMaxPx())
    }

    @Test fun `gave up leg uses the same column as the solved leg`() {
        rule.showGolden(dark = false) { UnderUpBar { LegResult(SHORT, gaveUp = true) } }
        assertTrue(rule.onNodeWithTag("result.next").fetchSemanticsNode().size.width <= columnMaxPx())
        assertTrue(rule.onNodeWithTag(ReplayTags.WATCH_ROW).fetchSemanticsNode().size.width <= columnMaxPx())
    }

    @Test fun `leg gave up short light`() = capture("leg-gave-up-short", dark = false) { LegResult(SHORT, gaveUp = true) }
    @Test fun `leg gave up short dark`() = capture("leg-gave-up-short", dark = true) { LegResult(SHORT, gaveUp = true) }
    @Test fun `leg gave up long light`() = capture("leg-gave-up-long", dark = false) { LegResult(LONG, gaveUp = true) }
    @Test fun `leg gave up long dark`() = capture("leg-gave-up-long", dark = true) { LegResult(LONG, gaveUp = true) }

    /** [scrollToReplay]: a surface whose content outgrows the screen (the custom board, a tablet's leg result) folds the replay row away, so it is brought into view; the others show the first screen. */
    private fun capture(name: String, dark: Boolean, scrollToReplay: Boolean = false, page: @Composable ColumnScope.() -> Unit) {
        rule.showGolden(dark) { UnderUpBar(page) }
        if (scrollToReplay) {
            // The scroll waits for the app to idle, which a frozen clock never reaches.
            rule.mainClock.autoAdvance = true
            rule.onNodeWithTag(ReplayTags.WATCH_ROW).performScrollTo()
        }
        rule.settleAndCapture("replay-results", name, device, dark)
    }

    @Composable
    private fun ColumnScope.OwnResult(timeline: Timeline) {
        val facts = ResultHeroFacts(
            timeMs = 100_000L,
            hintsUsed = 0,
            verdict = RecordVerdict.NotBest,
            previousBest = BestScore(90_000L, 0),
            rankOrder = RankOrder.TimeThenHints,
            game = "Queens",
            puzzleNumber = 918,
            practice = false,
            playAgain = false,
            dateLine = "Today · Sat 7 Nov",
            doneToday = 2,
            total = 3,
            insights = null,
            rank = ResultRank.Ranked(position = 14, count = 212, topPercent = 7),
            primary = ResultPrimaryAction.Next(GameId("tango"), "Tango"),
        )
        ResultHeroScreen(
            facts = facts,
            onSignIn = {},
            modifier = Modifier.weight(1f),
            brand = BRAND,
            actions = { ResultHeroActions(facts.primary, onPrimary = {}, onShare = {}, onPlayAgain = {}) },
            replay = { ReplaySection(ReplaySectionState.Ready(timeline), GoldenReplayUi, onOpen = {}) },
        )
    }

    @Composable
    private fun ColumnScope.CustomResult(timeline: Timeline) {
        CustomResultScreen(
            state = CUSTOM.copy(replay = ReplaySectionState.Ready(timeline)),
            onDone = {},
            onShare = {},
            onPlayAgain = {},
            onSignIn = {},
            modifier = Modifier.weight(1f),
            replayUi = GoldenReplayUi,
        )
    }

    @Composable
    private fun ColumnScope.LegResult(timeline: Timeline, gaveUp: Boolean) {
        Ready(
            ready = MatchLegResultState.Ready(
                number = 2,
                count = 3,
                opponent = "Sam",
                you = DuelSide("Ann", null, if (gaveUp) DuelSideState.GaveUp else DuelSideState.Time(100_000L, 0)),
                them = DuelSide("Sam", null, DuelSideState.Sealed),
                verdict = LegVerdict.Pending,
                rankOrder = RankOrder.TimeThenHints,
                posting = null,
                hero = if (gaveUp) null else LEG_HERO,
                brand = BRAND,
                next = LegNext.Puzzle(MatchLegKey("K7Q2MX", 2, "tango"), "Tango", 412),
                replay = ReplaySectionState.Ready(timeline),
                playUi = GoldenReplayUi,
                replayKey = ReplayKey.leg("queens", "K7Q2MX", 1),
            ),
            onNext = {},
            onBackToDuel = {},
            onOpenReplay = {},
        )
    }

    private companion object {
        const val LONG_MOVES = 64
        const val LONG_STEP_MS = 64_000L
        val SHORT: Timeline = ReplayFixtures.timeline(ReplayFixtures.SHORT_MOVES)
        val LONG: Timeline = ReplayFixtures.timeline(LONG_MOVES, stepMs = LONG_STEP_MS)
        val BRAND = GameBrand(AccentSlot.Violet, GameGlyphs.Queens)

        val LEG_HERO = ResultHeroFacts(
            timeMs = 100_000L,
            hintsUsed = 0,
            verdict = null,
            previousBest = null,
            rankOrder = RankOrder.TimeThenHints,
            game = "Queens",
            puzzleNumber = 918,
            practice = false,
            playAgain = false,
            dateLine = "Sat 7 Nov",
            doneToday = null,
            total = null,
            insights = null,
            rank = ResultRank.Hidden,
            primary = null,
        )

        val CUSTOM = CustomResultState(
            timeMs = 118_000L,
            hintsUsed = 0,
            title = "Queens 8×8 · Crown the castle",
            brand = GameBrand.Neutral.copy(accent = AccentSlot.Pink),
            rankOrder = RankOrder.TimeThenHints,
            puzzle = ReceivedPuzzle(
                code = requireNotNull(ShareCode.parse("K7Q2MX")),
                gameId = GameId("queens"),
                variant = VariantId("queens/n8"),
                sharedFormatVersion = 1,
                puzzleBytes = ByteArray(0),
                title = "Crown the castle",
                authorName = PreviewBoardAuthor.name,
                note = "",
                authorTimeMs = 102_000L,
                authorHints = 0,
                authorUid = "uid-jonas",
                openedAt = Instant.EPOCH,
                unpublished = false,
                postState = PostState.Pending,
                postAttempts = 0,
                difficulty = null,
            ),
            standing = CustomStanding.Record,
            board = CustomBoardView.Ranked(
                top = listOf(
                    previewBoardRow(1, "Maja", 74_000L),
                    previewBoardRow(2, "Oskar Bergström-Nilsson", 81_000L),
                    previewBoardRow(3, "Lea", 95_000L, hints = 1),
                    previewBoardRow(4, "Ines", 103_000L),
                    previewBoardRow(5, "Tom", 110_000L, hints = 2),
                ),
                mine = previewBoardRow(7, "Me", 118_000L, isMe = true),
                author = PreviewBoardAuthor,
            ),
        )
    }
}

/** A game with a replay board, which is all the replay section reads from its play UI. */
private object GoldenReplayUi : GamePlayUi {
    override val gameId: GameId = ReplayFixtures.GAME
    override val replayBoard: GameReplayBoard? = ReplayGoldenBoard
    override val tutorial: List<TutorialStep> get() = emptyList()

    override fun createController(env: PlayEnvironment): GamePlayController = error("golden only")

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
