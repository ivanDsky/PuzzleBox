package com.ivandsky.puzzlebox.games.strandsui.chrome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.games.strandsui.StrandsCopy
import com.ivandsky.puzzlebox.games.strandsui.VerdictKind
import com.ivandsky.puzzlebox.games.strandsui.WordSlot

/**
 * The line between the theme card and the board (A35): the live letters, then the verdict.
 * Its height is one pill line in every state, so the board never moves. A refusal's motion is
 * the board's, on the traced path; a verdict is a polite live region that reads its copy and
 * then the counter (SC12).
 */
@Composable
internal fun WordSlotLine(
    slot: WordSlot,
    found: Int,
    total: Int,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val lineHeight = with(density) { MaterialTheme.typography.titleLarge.lineHeight.toDp() }
    Box(
        modifier
            .fillMaxWidth()
            .height(lineHeight + Spacing.xs * 2)
            .clearAndSetSemantics {
                testTag = "strands.slot"
                when (slot) {
                    WordSlot.Empty -> Unit
                    is WordSlot.Tracing -> contentDescription = slot.letters
                    is WordSlot.Verdict -> {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = StrandsCopy.announcement(slot.copy, found, total)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when (slot) {
            WordSlot.Empty -> Unit
            is WordSlot.Tracing -> SlotText(slot.letters, MaterialTheme.colorScheme.onSurface)
            is WordSlot.Verdict -> VerdictContent(slot)
        }
    }
}

@Composable
private fun VerdictContent(verdict: WordSlot.Verdict) {
    val board = LocalBoardColors.current
    when (verdict.kind) {
        VerdictKind.Theme -> SlotPill(verdict.copy, board.strandsThemeFill, board.strandsTileText)
        VerdictKind.Spangram -> SlotPill(verdict.copy, board.strandsSpangramFill, board.strandsTileText)
        VerdictKind.NonTheme -> SlotText(verdict.copy, MaterialTheme.colorScheme.onSurfaceVariant)
        VerdictKind.Reject -> SlotText(verdict.copy, MaterialTheme.colorScheme.onSurface)
    }
}

/** Found words sit in a pill of their board colour, so the slot echoes the tiles. */
@Composable
private fun SlotPill(text: String, fill: Color, content: Color) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = fill, contentColor = content) {
        SlotText(text, content, Modifier.padding(horizontal = Spacing.md))
    }
}

@Composable
private fun SlotText(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = color,
        maxLines = 1,
        modifier = modifier,
    )
}
