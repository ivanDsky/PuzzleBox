package com.ivandsky.puzzlebox.compete.duel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.HiddenBoardTokens
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.rankRuleText

/** The least a shrunk board keeps; below it the column is taller than the pane and the page scrolls instead. */
private val MinBoardSide = 160.dp

/**
 * The tablet's right pane: the selected puzzle's blurred board, then the puzzle large with its status, the opponent's
 * line and, in a self duel, the DONE / IN THIS PUZZLE card,
 * then the Start / Resume button. The column fits [height]: the details keep their size and the
 * board takes the room they leave, at most the friend page's [HiddenBoardTokens.MaxSide] and at least [MinBoardSide].
 */
@Composable
internal fun LegDetail(row: LegRowModel, content: LegsContent, brands: GamePlayRegistry, height: Dp) {
    SubcomposeLayout { constraints ->
        val gap = Spacing.md.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        val details = subcompose("details") { LegDetails(row, content, brands) }.map { it.measure(loose) }
        val detailsHeight = details.sumOf { it.height }
        val side = (height.roundToPx() - detailsHeight - gap)
            .coerceIn(MinBoardSide.roundToPx(), HiddenBoardTokens.MaxSide.roundToPx())
        val board = subcompose("board") {
            key(row.legIndex) { LegBoardPreview(row.legIndex, sharp = row.status is LegStatus.Solved, maxSide = side.toDp(), board = content.board) }
        }.map { it.measure(loose) }
        val boardHeight = board.sumOf { it.height }
        val boardBlock = if (boardHeight > 0) boardHeight + gap else 0
        layout(constraints.maxWidth, boardBlock + detailsHeight) {
            board.forEach { it.place(0, 0) }
            details.forEach { it.place(0, boardBlock) }
        }
    }
}

@Composable
private fun LegDetails(row: LegRowModel, content: LegsContent, brands: GamePlayRegistry) {
    Column(Modifier.fillMaxWidth().testTag("duel.legs.detail"), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        GameGlyph(brands.brandOf(row.gameId), GameGlyphSize.Hero)
        Text(row.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(rankRuleText(row.rankOrder), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LegStatusPill(row.status)
        OpponentText(row.opponent, content.model.opponent.name)
        PostingNote(row.posting)
        if (content.model.self) Callout(done = row.action == null, timeLeftShown = content.timeLeftShown)
        LegActionButton(row, onClick = { content.actions.onOpenLeg(row) }, modifier = Modifier.fillMaxWidth())
    }
}
