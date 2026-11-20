package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.RecapCard as PaletteEntry
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.field
import com.ivandsky.puzzlebox.designsystem.ink

/**
 * The Fastest card's game tabs: one per game in deck order, the current one a pill in [ink] with the card's field on it
 * and the rest outlined in ink. A tap on a tab calls [onSelect] with its index. The row scrolls sideways when the
 * names don't fit, keeping the current tab in view, and reads as a tab list ("Fastest solve for Zip, 2 of 5").
 */
@Composable
internal fun FastestGameTabs(
    names: List<String>,
    current: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = PaletteEntry.Fastest.ink
    val field = PaletteEntry.Fastest.field
    val requesters = remember(names.size) { List(names.size) { BringIntoViewRequester() } }
    LaunchedEffect(current, names.size) { requesters.getOrNull(current)?.bringIntoView() }
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup()
            .testTag("recap.fastest.tabs"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        names.forEachIndexed { index, name ->
            val selected = index == current
            Box(
                Modifier
                    .bringIntoViewRequester(requesters[index])
                    .heightIn(min = TabHeight)
                    .clip(CircleShape)
                    .background(if (selected) ink else Color.Transparent)
                    .border(BorderStroke(TabOutline, ink), CircleShape)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                    .semantics { contentDescription = "Fastest solve for $name, ${index + 1} of ${names.size}" }
                    .padding(horizontal = Spacing.lg)
                    .testTag("recap.fastest.tab.$index"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name,
                    Modifier.clearAndSetSemantics {},
                    color = if (selected) field else ink,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                    maxLines = 1,
                )
            }
        }
    }
}

private val TabHeight = 48.dp
private val TabOutline = 1.5.dp

@PuzzleBoxPreviews
@Composable
private fun FastestGameTabsPreview() = PuzzleBoxTheme {
    Surface(color = PaletteEntry.Fastest.field) {
        FastestGameTabs(listOf("Queens", "Tango", "Zip", "Mini Sudoku", "Strands"), current = 2, onSelect = {}, modifier = Modifier.padding(Spacing.lg))
    }
}
