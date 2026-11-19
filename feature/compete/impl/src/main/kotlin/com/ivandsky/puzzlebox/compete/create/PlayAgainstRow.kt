package com.ivandsky.puzzlebox.compete.create

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.resultui.PastYouAvatar

private val CHOICE_WIDTH = 72.dp
private val AVATAR = 40.dp
private val RING = 48.dp
private val RING_WIDTH = 2.dp
private val DASH_WIDTH = 2.dp
private const val DASH_ON = 6f
private const val DASH_OFF = 4f

/**
 * "Play against": the label on the left and the two choices on the right, "A friend" (a dashed "?") and "Past you"
 * (your [selfName]'s initial with the past mark). The [selected] one wears a primary ring that crossfades over
 * [MotionTokens.SelectDuration] (instant with reduced motion) and its label turns bold.
 */
@Composable
internal fun PlayAgainstRow(selected: Opponent, selfName: String, onSelect: (Opponent) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "PLAY AGAINST",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Choice("A friend", selected == Opponent.Friend, { onSelect(Opponent.Friend) }, "duel.opponent.friend") { UnknownFriend() }
            Choice("Past you", selected == Opponent.PastYou, { onSelect(Opponent.PastYou) }, "duel.opponent.self") {
                PastYouAvatar(name = selfName, size = AVATAR)
            }
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit, tag: String, avatar: @Composable () -> Unit) {
    val reduced = rememberReducedMotion()
    val spec = if (reduced) snap<Color>() else tween(MotionTokens.SelectDuration)
    val ring by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, spec, label = "opponent ring")
    val caption by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, spec, label = "opponent label",
    )
    Column(
        modifier = Modifier
            .width(CHOICE_WIDTH)
            .defaultMinSize(minHeight = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(RING).border(RING_WIDTH, ring, CircleShape), contentAlignment = Alignment.Center) { avatar() }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = caption,
            textAlign = TextAlign.Center,
        )
    }
}

/** A friend not yet picked: a dashed circle holding "?". */
@Composable
private fun UnknownFriend() {
    val dash = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(AVATAR)
            .drawBehind {
                val stroke = DASH_WIDTH.toPx()
                drawCircle(
                    color = dash,
                    radius = (size.minDimension - stroke) / 2,
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_ON.dp.toPx(), DASH_OFF.dp.toPx()))),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("?", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@PuzzleBoxPreviews
@Composable
internal fun PlayAgainstRowFriendPreview() = PuzzleBoxTheme { PlayAgainstRow(Opponent.Friend, "I", onSelect = {}) }

@PuzzleBoxPreviews
@Composable
internal fun PlayAgainstRowPastYouPreview() = PuzzleBoxTheme { PlayAgainstRow(Opponent.PastYou, "I", onSelect = {}) }
