package com.ivandsky.puzzlebox.train.drill

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.Restored
import com.ivandsky.puzzlebox.gameui.TutorialStep
import com.ivandsky.puzzlebox.gameui.trainer.DrillCandidate
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.train.round.StandInSnippet
import com.ivandsky.puzzlebox.train.round.standInBoard

// The drill: the page while it loads, the needs-a-connection and can't-load messages, the play screen and the solved page.
// This module sees no game and no play feature, so the play screen is a stand-in: a top bar with Up, the technique's name and
// "Practice puzzle" (and no clock) over the same plain grid the round's previews draw.

/** A play UI with no game behind it: it only stands in the drill state's `ui` and `controller`, which the stand-in slot never reads. */
internal object StandInPlayUi : GamePlayUi {
    override val gameId = GameId("queens")
    override val tutorial: List<TutorialStep> = emptyList()

    override fun createController(env: PlayEnvironment): GamePlayController = StandInController

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

/** The controller [StandInPlayUi] hands out: never played. */
internal object StandInController : GamePlayController {
    override val isComplete = false

    override fun adoptToday(puzzle: Puzzle, ref: PuzzleRef): Adopt = Adopt.Adopted

    override fun adoptRestored(bytes: ByteArray): Restored? = null

    override fun playAgain() = Unit
}

/** The drill state of a board being played. */
internal val standInPlaying = DrillState.Playing(StandInPlayUi, StandInController, won = false)

/** The play screen a stand-in game draws: the bar the sandbox draws without a clock, over the stand-in grid. */
internal val standInDrillPlay = DrillPlaySlot { _, _, title, label, _, onBack, _, modifier ->
    Column(modifier) {
        Row(Modifier.padding(horizontal = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, Modifier.size(48.dp)) { Icon(PuzzleIcons.Back, contentDescription = "Back") }
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }
        standInBoard.board(StandInSnippet(), Modifier.padding(horizontal = Spacing.lg).fillMaxSize())
    }
}

@Composable
private fun DrillPreviewFrame(state: DrillState) = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        DrillScreen(
            name = "Line lock",
            state = state,
            showBack = true,
            onClose = {},
            play = standInDrillPlay,
            onSolved = {},
            onTryAgain = {},
            onTryAnother = {},
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )
    }
}

@PuzzleBoxPreviews
@Composable
private fun DrillLoadingPreview() = DrillPreviewFrame(DrillState.Loading)

@PuzzleBoxPreviews
@Composable
private fun DrillNeedsConnectionPreview() = DrillPreviewFrame(DrillState.NeedsConnection)

@PuzzleBoxPreviews
@Composable
private fun DrillCantLoadPreview() = DrillPreviewFrame(DrillState.CantLoad(DrillCandidate.Archive(number = 918, targetSteps = 4)))

@PuzzleBoxPreviews
@Composable
private fun DrillPlayingPreview() = DrillPreviewFrame(standInPlaying)

@PuzzleBoxPreviews
@Composable
private fun DrillSolvedPreview() = DrillPreviewFrame(DrillState.Solved)
