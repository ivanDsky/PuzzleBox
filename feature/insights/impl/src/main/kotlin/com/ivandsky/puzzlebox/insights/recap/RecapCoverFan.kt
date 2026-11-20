package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.RecapMotion
import com.ivandsky.puzzlebox.designsystem.colors

/**
 * Five game letters fanned like a hand of cards, the cover's art: Queens, Tango, Zip, Mini Sudoku and Strands, each in
 * its game's accent. The cards grow with the width, up to [MaxCardWidth], and are ink-outlined so they hold on the
 * cover's plate. From [startMs] on they spread out of one stack, one after another on the DeckSpring; under reduced
 * motion they sit fanned. Decoration only, so a screen reader skips it.
 */
@Composable
internal fun RecapCoverFan(modifier: Modifier = Modifier, startMs: Int = 0) {
    val ink = LocalContentColor.current
    BoxWithConstraints(modifier.clearAndSetSemantics {}) {
        val card = (maxWidth * CardWidthShare).coerceAtMost(MaxCardWidth)
        val cardHeight = card * CardAspect
        val step = ((maxWidth - card) * StepShare).coerceAtMost(card * MaxStepShare)
        Box(Modifier.size(maxWidth, cardHeight + ArcDrop * 4)) {
            Letters.forEachIndexed { i, (slot, letter) ->
                val k = i - Letters.lastIndex / 2
                val accent = slot.colors()
                val spread = coverEntrance(
                    startMs + i * MotionTokens.EnterStagger,
                    spring(RecapMotion.DeckSpringDamping, RecapMotion.DeckSpringStiffness),
                )
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .offset(x = step * k, y = ArcDrop * (k * k) - ArcDrop * 2)
                        .size(card, cardHeight)
                        .graphicsLayer {
                            val p = spread.value
                            // Out of the stack: from under the middle card, lower and upright, into its place in the fan.
                            translationX = (p - 1f) * (step * k).toPx()
                            translationY = (1f - p) * RecapMotion.DealRise.toPx()
                            rotationZ = p * k * TiltStep
                            alpha = p.coerceIn(0f, 1f)
                        }
                        .clip(CardShape)
                        .background(accent.accent)
                        .border(BorderStroke(Outline, ink), CardShape)
                        .padding(Outline)
                        .border(BorderStroke(Inline, Color.White), InnerShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        letter,
                        color = accent.onAccent,
                        style = MaterialTheme.typography.displayMedium,
                        fontSize = (card.value * LetterShare).sp,
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic,
                    )
                }
            }
        }
    }
}

private val Letters = listOf(
    AccentSlot.Violet to "Q",
    AccentSlot.Orange to "T",
    AccentSlot.Blue to "Z",
    AccentSlot.Teal to "M",
    AccentSlot.Pink to "S",
)

private val MaxCardWidth: Dp = 120.dp
private val ArcDrop = 5.dp
private val Outline = 2.dp
private val Inline = 1.5.dp
private val CardShape = RoundedCornerShape(20.dp)
private val InnerShape = RoundedCornerShape(18.dp)

private const val CardWidthShare = 0.33f
private const val CardAspect = 1.38f

/** How much of the room left beside one card the neighbouring cards step across, and the most, in card widths. */
private const val StepShare = 0.215f
private const val MaxStepShare = 1.13f

/** Each card turns this many degrees further than its inner neighbour. */
private const val TiltStep = 9f

/** The letter's size in sp per dp of card width. */
private const val LetterShare = 0.4f
