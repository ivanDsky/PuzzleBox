package com.ivandsky.puzzlebox.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.SummaryLinkRow
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId

internal val RecapPreviewBrands: Map<GameId, GameBrand> = mapOf(
    GameId("zip") to GameBrand(AccentSlot.Blue, GameGlyphs.Zip),
    GameId("tango") to GameBrand(AccentSlot.Orange, GameGlyphs.Tango),
    GameId("queens") to GameBrand(AccentSlot.Violet, GameGlyphs.Queens),
    GameId("sudoku") to GameBrand(AccentSlot.Teal, GameGlyphs.Sudoku),
    GameId("strands") to GameBrand(AccentSlot.Pink, GameGlyphs.Strands),
)

internal fun previewRecap(
    label: String = "October 2026",
    periodId: String = "2026-10",
    yearly: Boolean = false,
    opened: Boolean = false,
    onOpen: () -> Unit = {},
    onDismiss: () -> Unit = {},
) = RecapSlot.Due(
    periodId = periodId,
    label = label,
    yearly = yearly,
    solved = if (yearly) 1_240 else 96,
    solvedByGame = listOf(GameId("zip") to 30, GameId("tango") to 26, GameId("queens") to 20, GameId("sudoku") to 12, GameId("strands") to 8),
    months = if (yearly) listOf(48, 62, 80, 91, 104, 110, 98, 121, 130, 142, 118, 136) else emptyList(),
    opened = opened,
    onOpen = onOpen,
    onDismiss = onDismiss,
)

// Preview: the tile above another summary row at the 360 dp floor, then a long month name that must not clip, the opened
// tile, the year tile and the skeleton.
@PuzzleBoxPreviews
@Composable
internal fun HomeRecapCardPhonePreview() {
    PuzzleBoxTheme {
        Column(
            Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.lg).width(360.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            HomeRecapCard(previewRecap(), RecapPreviewBrands)
            SummaryLinkRow(text = "Today's rank: #3 on Zip", onClick = {})
            HomeRecapCard(previewRecap(label = "September 2026 in a very long language"), RecapPreviewBrands)
            HomeRecapCard(previewRecap(opened = true), RecapPreviewBrands)
            HomeRecapCard(previewRecap(label = "2026", periodId = "2026", yearly = true), RecapPreviewBrands)
            HomeRecapCard(RecapSlot.Loading, RecapPreviewBrands)
        }
    }
}
