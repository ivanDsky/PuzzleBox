package com.ivandsky.puzzlebox.train

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.train.domain.RecordAnswer
import com.ivandsky.puzzlebox.train.drill.DrillScreen
import com.ivandsky.puzzlebox.train.drill.DrillState
import com.ivandsky.puzzlebox.train.drill.standInDrillPlay
import com.ivandsky.puzzlebox.train.drill.standInPlaying
import com.ivandsky.puzzlebox.train.round.RoundPane
import com.ivandsky.puzzlebox.train.round.StandInLine
import com.ivandsky.puzzlebox.train.round.RoundState
import com.ivandsky.puzzlebox.train.round.standInBoard
import com.ivandsky.puzzlebox.train.round.standInQuestion
import com.ivandsky.puzzlebox.train.tab.TechniqueList
import com.ivandsky.puzzlebox.train.tab.previewGames
import com.ivandsky.puzzlebox.train.tab.previewTechniques
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Review renders of the Train pane (ADR-0033: rendered on demand, never committed): the technique list, and a round
 * before an answer and after a Right with the Spotlight. The board is a stand-in grid, as this module sees no game's
 * board. Each render sits in the padding the Games screen gives it: the phone's page inset, or the tablet's 400dp list
 * column with the round or the placeholder beside it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class TrainPhoneGoldenScreenshots : TrainGoldenScreenshots("phone") {
    @Test fun `list light`() = capture("list", dark = false) { Page { List(open = false) } }
    @Test fun `list dark`() = capture("list", dark = true) { Page { List(open = false) } }

    @Test fun `round before answer light`() = capture("round-before", dark = false) { Page { Round(Before, showBack = true) } }
    @Test fun `round before answer dark`() = capture("round-before", dark = true) { Page { Round(Before, showBack = true) } }

    @Test fun `round right light`() = capture("round-right", dark = false) { Page { Round(Right, showBack = true) } }
    @Test fun `round right dark`() = capture("round-right", dark = true) { Page { Round(Right, showBack = true) } }

    @Test fun `summary light`() = capture("summary", dark = false) { Page { Round(RoundState.Summary(4, 5), showBack = true) } }
    @Test fun `empty light`() = capture("empty", dark = false) { Page { Round(RoundState.Empty, showBack = true) } }
    @Test fun `needs connection light`() = capture("needs-connection", dark = false) { Page { Round(RoundState.NeedsConnection, showBack = true) } }
    @Test fun `cant load light`() = capture("cant-load", dark = false) { Page { Round(RoundState.CantLoad(alternative = 918), showBack = true) } }

    @Test fun `drill playing light`() = capture("drill-playing", dark = false) { Page { Drill(standInPlaying, showBack = true) } }
    @Test fun `drill playing dark`() = capture("drill-playing", dark = true) { Page { Drill(standInPlaying, showBack = true) } }
    @Test fun `drill solved light`() = capture("drill-solved", dark = false) { Page { Drill(DrillState.Solved, showBack = true) } }
    @Test fun `drill needs connection light`() = capture("drill-needs-connection", dark = false) { Page { Drill(DrillState.NeedsConnection, showBack = true) } }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class TrainTabletGoldenScreenshots : TrainGoldenScreenshots("tablet") {
    @Test fun `list with the placeholder light`() = capture("list", dark = false) { Beside(open = false) { Placeholder() } }

    @Test fun `round before answer light`() = capture("round-before", dark = false) { Beside(open = true) { Round(Before, showBack = false) } }
    @Test fun `round before answer dark`() = capture("round-before", dark = true) { Beside(open = true) { Round(Before, showBack = false) } }

    @Test fun `round right light`() = capture("round-right", dark = false) { Beside(open = true) { Round(Right, showBack = false) } }
    @Test fun `round right dark`() = capture("round-right", dark = true) { Beside(open = true) { Round(Right, showBack = false) } }

    @Test fun `drill playing light`() = capture("drill-playing", dark = false) { Beside(open = true) { Drill(standInPlaying, showBack = false) } }
    @Test fun `drill playing dark`() = capture("drill-playing", dark = true) { Beside(open = true) { Drill(standInPlaying, showBack = false) } }
    @Test fun `drill solved light`() = capture("drill-solved", dark = false) { Beside(open = true) { Drill(DrillState.Solved, showBack = false) } }
}

abstract class TrainGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    internal fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        rule.showGolden(dark, content)
        rule.settleAndCapture("train", name, device, dark)
    }

    internal companion object {
        val Before: RoundState = standInQuestion(answer = null)
        val Right: RoundState = standInQuestion(RecordAnswer.Answer.Verdict(TrainerVerdict.Right), right = 3)

        @Composable
        fun List(open: Boolean) = TechniqueList(
            games = previewGames,
            selected = previewGames.first().id,
            techniques = previewTechniques,
            openId = TechniqueId("line-lock").takeIf { open },
            onSelectGame = {},
            onOpen = {},
            modifier = Modifier.fillMaxSize(),
        )

        @Composable
        fun Round(state: RoundState, showBack: Boolean) = RoundPane(
            name = "Line lock",
            line = StandInLine,
            state = state,
            showBack = showBack,
            onClose = {},
            board = standInBoard,
            onShowMe = {},
            onNext = {},
            onTryAgain = {},
            onTryAnother = {},
            onDrill = {},
        )

        @Composable
        fun Drill(state: DrillState, showBack: Boolean) = DrillScreen(
            name = "Line lock",
            state = state,
            showBack = showBack,
            onClose = {},
            play = standInDrillPlay,
            onSolved = {},
            onTryAgain = {},
            onTryAnother = {},
        )

        @Composable
        fun Placeholder() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Pick a technique", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        /** The phone's Train page: the pager page's side inset and top gap. */
        @Composable
        fun Page(content: @Composable () -> Unit) =
            Box(Modifier.fillMaxSize().padding(horizontal = Spacing.lg).padding(top = Spacing.sm)) { content() }

        /** The tablet's two panes as the Library lays them: the list column, a gap, then the right pane. */
        @Composable
        fun Beside(open: Boolean, pane: @Composable () -> Unit) = Row(
            Modifier.fillMaxSize().padding(start = Spacing.xl, top = Spacing.lg, end = Spacing.xxl, bottom = Spacing.xl),
        ) {
            Box(Modifier.width(400.dp).padding(horizontal = 6.dp).padding(top = Spacing.sm)) { List(open) }
            Spacer(Modifier.width(Spacing.xl))
            Box(Modifier.weight(1f)) { pane() }
        }
    }
}
