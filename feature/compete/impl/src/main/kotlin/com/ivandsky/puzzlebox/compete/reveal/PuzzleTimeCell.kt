package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.DuelTone
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.Stamp
import com.ivandsky.puzzlebox.designsystem.tabular
import kotlinx.coroutines.delay

private val TickSlot = 24.dp
private val TickIcon = 22.dp
private val TabletCellHeight = 52.dp
private val PhoneCellHeight = 44.dp

/** What a puzzle did for one side, as its time cell shows it. */
internal enum class CellResult { Won, Lost, Tied, Missing }

/**
 * One player's time in one puzzle: right-aligned and tabular in the cell, then a tick slot that is always there, so the
 * figures keep their place whether or not the cell is ticked. A [CellResult.Won] cell is tinted in the player's [tone]
 * with a check that stamps in after [tickDelayMs]; a tie is a neutral cell with "=" in the slot; the loser's time is
 * muted; [CellResult.Missing] ("Gave up", "No result") is smaller and muted. [hints], where the game ranks on them, sits
 * under the time.
 */
@Composable
internal fun PuzzleTimeCell(
    time: String,
    hints: String?,
    result: CellResult,
    tone: DuelTone,
    compact: Boolean,
    tickDelayMs: Int,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when {
        result == CellResult.Won && tone == DuelTone.Me -> scheme.primaryContainer to scheme.onPrimaryContainer
        result == CellResult.Won -> scheme.secondaryContainer to scheme.onSecondaryContainer
        result == CellResult.Tied -> scheme.surfaceContainerHigh to scheme.onSurface
        else -> Color.Transparent to scheme.onSurfaceVariant
    }
    val base = MaterialTheme.typography.titleLarge
    val style = when {
        result == CellResult.Missing -> base.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
        compact -> base.copy(fontSize = 18.sp, lineHeight = 22.sp)
        else -> base.copy(fontSize = 22.sp, lineHeight = 26.sp)
    }.tabular()
    Row(
        modifier = modifier
            .height(if (compact) PhoneCellHeight else TabletCellHeight)
            .background(container, MaterialTheme.shapes.small)
            .padding(start = if (compact) Spacing.sm else Spacing.md, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(
                text = time,
                style = style,
                fontWeight = if (result == CellResult.Lost) FontWeight.SemiBold else style.fontWeight,
                color = content,
                textAlign = TextAlign.End,
                maxLines = 1,
                softWrap = false,
            )
            if (hints != null) {
                Text(
                    text = hints,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold),
                    color = if (result == CellResult.Won) content else scheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        Box(Modifier.size(TickSlot), contentAlignment = Alignment.Center) {
            when (result) {
                CellResult.Won -> Tick(content, tickDelayMs)
                CellResult.Tied -> Text("=", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = content)
                else -> Unit
            }
        }
    }
}

/** The winner's check, stamped in once the row has arrived (at once in a preview). */
@Composable
private fun Tick(tint: Color, delayMs: Int) {
    val inspecting = LocalInspectionMode.current
    var shown by remember { mutableStateOf(inspecting) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        shown = true
    }
    Stamp(visible = shown) {
        Icon(PuzzleIcons.Check, contentDescription = "Won", modifier = Modifier.size(TickIcon), tint = tint)
    }
}

@PuzzleBoxPreviews
@Composable
internal fun PuzzleTimeCellPreview() = PuzzleBoxTheme {
    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        PuzzleTimeCell("0:42", null, CellResult.Won, DuelTone.Me, compact = false, tickDelayMs = 0, modifier = Modifier.width(188.dp))
        PuzzleTimeCell("3:10", "0 hints", CellResult.Won, DuelTone.Them, compact = false, tickDelayMs = 0, modifier = Modifier.width(188.dp))
        PuzzleTimeCell("0:54", null, CellResult.Lost, DuelTone.Them, compact = true, tickDelayMs = 0, modifier = Modifier.width(100.dp))
        PuzzleTimeCell("0:41", null, CellResult.Tied, DuelTone.Me, compact = true, tickDelayMs = 0, modifier = Modifier.width(100.dp))
        PuzzleTimeCell("No result", null, CellResult.Missing, DuelTone.Me, compact = true, tickDelayMs = 0, modifier = Modifier.width(100.dp))
    }
}
