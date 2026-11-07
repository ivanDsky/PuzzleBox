package com.ivandsky.puzzlebox.games.strandsui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.games.strandsui.FoundWord
import com.ivandsky.puzzlebox.games.strandsui.StrandsCopy
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsBoardSpec

/**
 * The tablet side panel's found list: each found word as a chip in its board colour, green
 * for a theme word and gold with the board's inset ring for the spangram (A32's second cue),
 * then one dashed slot per word still to find. A slot carries no length, so it gives
 * nothing away; TalkBack hears the found words and skips the slots.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FoundWordsList(words: List<FoundWord>, total: Int, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag("strands.found_words"), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = StrandsCopy.FOUND_WORDS,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // Theme words alternate pink and magenta in found order, like their tokens on the board.
            var themes = 0
            for (word in words) FoundChip(word, alt = !word.spangram && themes++ % 2 == 1)
            repeat((total - words.size).coerceAtLeast(0)) { MissingSlot() }
        }
    }
}

@Composable
private fun FoundChip(word: FoundWord, alt: Boolean) {
    val board = LocalBoardColors.current
    val ring = board.strandsTileText
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(ChipHeight)
            .clip(CircleShape)
            .background(
                when {
                    word.spangram -> board.strandsSpangramFill
                    alt -> board.strandsThemeAltFill
                    else -> board.strandsThemeFill
                },
            )
            .then(if (word.spangram) Modifier.drawBehind { insetRing(ring) } else Modifier)
            .padding(horizontal = ChipPadding)
            .testTag(if (word.spangram) "strands.found_spangram" else "strands.found_word")
            .clearAndSetSemantics { contentDescription = if (word.spangram) "${word.text}, spangram" else word.text },
    ) {
        Text(
            text = word.text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = board.strandsTileText,
        )
    }
}

@Composable
private fun MissingSlot() {
    val outline = MaterialTheme.colorScheme.outline
    Box(
        Modifier
            .size(width = SlotWidth, height = ChipHeight)
            .drawBehind { pill(outline, inset = 0f, PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))) }
            .testTag("strands.missing_word")
            .clearAndSetSemantics {},
    )
}

private fun DrawScope.insetRing(color: Color) = pill(color, inset = StrandsBoardSpec.SpangramRingInset.toPx(), effect = null)

private fun DrawScope.pill(color: Color, inset: Float, effect: PathEffect?) {
    val width = StrandsBoardSpec.SpangramRingWidth.toPx()
    val edge = inset + width / 2
    val radius = size.height / 2 - edge
    drawRoundRect(
        color = color,
        topLeft = Offset(edge, edge),
        size = Size(size.width - edge * 2, size.height - edge * 2),
        cornerRadius = CornerRadius(radius, radius),
        style = Stroke(width = width, pathEffect = effect),
    )
}

private val ChipHeight = 36.dp
private val ChipPadding = 14.dp
private val SlotWidth = 64.dp
