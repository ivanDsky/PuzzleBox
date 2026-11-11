package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.TutorialStep
import com.ivandsky.puzzlebox.model.GameId

/**
 * The games' accent hues without the games: compete-impl cannot depend on the game modules, so each golden game
 * gets the neutral glyph in the accent its real brand uses. Only the brand is real; the play surface is never shown.
 */
internal fun goldenBrands(): GamePlayRegistry = GamePlayRegistry(
    listOf(
        BrandOnlyPlay(GameId("zip"), AccentSlot.Blue),
        BrandOnlyPlay(GameId("queens"), AccentSlot.Violet),
        BrandOnlyPlay(GameId("strands"), AccentSlot.Teal),
    ),
)

private class BrandOnlyPlay(override val gameId: GameId, accent: AccentSlot) : GamePlayUi {
    override val brand: GameBrand = GameBrand.Neutral.copy(accent = accent)
    override val tutorial: List<TutorialStep> = emptyList()

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
