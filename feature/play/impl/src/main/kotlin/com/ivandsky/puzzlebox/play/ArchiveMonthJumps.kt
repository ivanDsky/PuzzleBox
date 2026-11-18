package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.GameAccent
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.pressScale
import com.ivandsky.puzzlebox.designsystem.pressTint
import com.ivandsky.puzzlebox.designsystem.tabular
import java.time.YearMonth

private val ChipGap = 6.dp
private val ChipHeight = 36.dp
private val ChipPadding = 14.dp
private val RandomIcon = 18.dp

/**
 * The calendar's jump row: "#1" / years / "Today" ([nav]'s jumps for [month]), then, with a non-null [onRandom],
 * a Random chip (`library.page.random`) in [accent]'s container so it reads as an action beside the neutral jumps.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ArchiveMonthJumps(
    nav: ArchiveMonthNav,
    month: YearMonth,
    accent: GameAccent,
    reduced: Boolean,
    onMonth: (YearMonth) -> Unit,
    onToday: () -> Unit,
    onRandom: (() -> Unit)?,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = ChipGap, bottom = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(ChipGap),
        verticalArrangement = Arrangement.spacedBy(ChipGap),
    ) {
        val jumps = nav.jumps(month)
        jumps.forEachIndexed { index, jump ->
            val name = when (index) {
                0 -> "first"
                jumps.lastIndex -> "today"
                else -> jump.label
            }
            JumpChip(jump, "library.calendar.jump.$name", reduced) {
                if (index == jumps.lastIndex) onToday() else onMonth(jump.month)
            }
        }
        onRandom?.let { RandomChip(accent, reduced, it) }
    }
}

@Composable
private fun JumpChip(jump: MonthJump, tag: String, reduced: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .pressScale(interaction, reduced)
            .height(ChipHeight)
            .clip(PillShape)
            .background(if (jump.selected) scheme.onSurface else scheme.surfaceContainerHighest)
            .pressTint(interaction, PillShape, if (jump.selected) scheme.surface else scheme.onSurface)
            .selectable(selected = jump.selected, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = ChipPadding)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            jump.label,
            style = MaterialTheme.typography.labelLarge.tabular(),
            color = if (jump.selected) scheme.surface else scheme.onSurface,
        )
    }
}

/** A jump's size and press, filled with the accent container; an action, so never selected. */
@Composable
private fun RandomChip(accent: GameAccent, reduced: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .pressScale(interaction, reduced)
            .height(ChipHeight)
            .clip(PillShape)
            .background(accent.container)
            .pressTint(interaction, PillShape, accent.onContainer)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = ChipPadding)
            .testTag("library.page.random"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PuzzleIcons.Dice, contentDescription = null, modifier = Modifier.size(RandomIcon), tint = accent.onContainer)
        Spacer(Modifier.width(Spacing.sm))
        Text("Random", style = MaterialTheme.typography.labelLarge, color = accent.onContainer)
    }
}
