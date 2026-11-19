package com.ivandsky.puzzlebox.train.tab

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress.Mark
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.trainer.TrainerTechnique
import com.ivandsky.puzzlebox.model.GameId

// The technique list: nothing open (the phone's list, the tablet's before a pick) and one technique open (the tablet,
// the open row drawn as selected). The rows are a Queens-like list with the three marks.

internal val previewGames = listOf(
    GameChip(GameId("queens"), "Queens", GameBrand.Neutral),
    GameChip(GameId("tango"), "Tango", GameBrand.Neutral),
)

internal val previewTechniques = listOf(
    TechniqueRow(
        TrainerTechnique(TechniqueId("singles"), "Singles", "A region or line has one open square left, so the queen goes there."),
        TechniqueProgress(Mark.Mastered, inRow = 5, answeredAtMs = 0),
    ),
    TechniqueRow(
        TrainerTechnique(TechniqueId("line-lock"), "Line lock", "A region sits inside one line, so the rest of that line is out."),
        TechniqueProgress(Mark.Practicing, inRow = 3, answeredAtMs = 0),
    ),
    TechniqueRow(
        TrainerTechnique(TechniqueId("shared-squares"), "Shared squares", "Two regions share squares, so neither queen can sit elsewhere."),
        TechniqueProgress(Mark.Practicing, inRow = 0, answeredAtMs = 0),
    ),
    TechniqueRow(
        TrainerTechnique(TechniqueId("squeeze"), "Squeeze", "A few regions fill a few lines, so no other queen can use those lines."),
        TechniqueProgress.NEW,
    ),
)

@PuzzleBoxPreviews
@Composable
private fun TechniqueListPreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        TechniqueList(previewGames, previewGames.first().id, previewTechniques, openId = null, onSelectGame = {}, onOpen = {}, Modifier.padding(horizontal = Spacing.lg))
    }
}

@PuzzleBoxPreviews
@Composable
private fun TechniqueListOpenPreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        TechniqueList(
            previewGames,
            previewGames.first().id,
            previewTechniques,
            openId = TechniqueId("line-lock"),
            onSelectGame = {},
            onOpen = {},
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )
    }
}
