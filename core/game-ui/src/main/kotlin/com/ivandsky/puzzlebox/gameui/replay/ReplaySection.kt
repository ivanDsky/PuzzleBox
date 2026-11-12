package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.TutorialStep
import com.ivandsky.puzzlebox.model.GameId

/**
 * The replay section of a result-like screen. The one place that reads ui.replayBoard:
 * Hidden, or a game with no replay board, draws nothing. Ready draws ReplayPlayer.
 */
@Composable
fun ReplaySection(state: ReplaySectionState, ui: GamePlayUi, modifier: Modifier = Modifier) {
    if (state !is ReplaySectionState.Ready) return
    val board = ui.replayBoard ?: return
    ReplayPlayer(state.timeline, board, modifier)
}

@PuzzleBoxPreviews
@Composable
internal fun ReplaySectionPreview() {
    PuzzleBoxTheme { ReplaySection(ReplaySectionState.Hidden, PreviewGame) }
}

private object PreviewGame : GamePlayUi {
    override val gameId = GameId("preview")
    override val tutorial: List<TutorialStep> = emptyList()

    override fun createController(env: PlayEnvironment): GamePlayController = error("preview only")

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
