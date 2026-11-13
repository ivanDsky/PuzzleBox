package com.ivandsky.puzzlebox.games.strandsui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.TutorialStep
import com.ivandsky.puzzlebox.gameui.replay.GameReplayBoard
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.model.GameId

/** Strands' one play registration (ADR-0001): its controller, its board and its play screen. */
class StrandsPlayUi : GamePlayUi {
    override val gameId: GameId get() = StrandsDefinition.id

    override fun createController(env: PlayEnvironment): GamePlayController = StrandsPlayController(env)

    override val brand: GameBrand get() = StrandsBrand

    override val tutorial: List<TutorialStep> get() = StrandsTutorial.steps

    override val replayBoard: GameReplayBoard get() = StrandsReplayBoardNoErrors

    @Composable
    override fun Board(controller: GamePlayController, modifier: Modifier) {
        StrandsBoard(controller as StrandsPlayController, modifier)
    }

    @Composable
    override fun Screen(
        controller: GamePlayController,
        status: PlayBoardStatus,
        layout: PlayLayout,
        sidePanel: (@Composable ColumnScope.() -> Unit)?,
        onRetry: () -> Unit,
        onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
        modifier: Modifier,
    ) {
        StrandsPlayScreen(controller as StrandsPlayController, status, layout, sidePanel, onRetry, onSolved, modifier)
    }
}
