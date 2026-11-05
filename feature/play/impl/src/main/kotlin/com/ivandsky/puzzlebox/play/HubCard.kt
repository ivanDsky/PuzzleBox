package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.gameui.todayStatusLine
import com.ivandsky.puzzlebox.model.GameId

/** A13: the stacked card's height on phones, about two thumbs tall. */
private val HubCardMinHeight = 120.dp

/**
 * One game's hub card (tag `hub.row.<gameId>`, A13): the game's picture (its Hero glyph
 * on its brand's accent container), its name, today's puzzle number, today's status
 * ([todayStatusLine], `hub.status.<gameId>`) and the [best] daily time (`hub.best.<gameId>`),
 * then what a tap does ([actionLabel], `hub.action.<gameId>`). Only the [isNext] card's
 * button is a filled pill in the game's accent (`hub.filled.<gameId>`); every other one
 * is tonal (`hub.tonal.<gameId>`). No status or button until the status is known. The
 * whole card and its button both call [onPlay].
 */
@Composable
internal fun HubCard(
    entry: HubRow,
    best: Long?,
    isNext: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val id = entry.game.value
    OutlinedCard(
        onClick = onPlay,
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .testTag("hub.row.$id"),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = HubCardMinHeight)
                .padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GameGlyph(entry.brand, GameGlyphSize.Hero, solid = false)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                entry.status?.let { status ->
                    Text(
                        text = todayStatusLine(status),
                        style = MaterialTheme.typography.bodyMedium.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("hub.status.$id"),
                    )
                }
                Details(entry.puzzleNumber, best, id)
            }
            entry.status?.let { status -> PlayButton(actionLabel(status), entry.brand, isNext, id, onPlay) }
        }
    }
}

/** "#592" and "Best 0:42", each only when known. */
@Composable
private fun Details(puzzleNumber: Int?, best: Long?, id: String) {
    if (puzzleNumber == null && best == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        val style = MaterialTheme.typography.bodySmall.tabular()
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        puzzleNumber?.let { Text(text = "#$it", style = style, color = color) }
        best?.let {
            Text(
                text = "Best ${formatDuration(it)}",
                style = style,
                color = color,
                modifier = Modifier.testTag("hub.best.$id"),
            )
        }
    }
}

/** The card's button: a filled accent pill on the next game, a tonal accent pill otherwise. */
@Composable
private fun PlayButton(label: String, brand: GameBrand, filled: Boolean, id: String, onPlay: () -> Unit) {
    val accent = brand.accent.colors()
    val padding = PaddingValues(horizontal = Spacing.lg)
    val text: @Composable () -> Unit = {
        Text(text = label, modifier = Modifier.testTag("hub.action.$id"))
    }
    if (filled) {
        Button(
            onClick = onPlay,
            shape = PillShape,
            contentPadding = padding,
            colors = ButtonDefaults.buttonColors(containerColor = accent.accent, contentColor = accent.onAccent),
            modifier = Modifier.heightIn(min = 48.dp).testTag("hub.filled.$id"),
        ) { text() }
    } else {
        FilledTonalButton(
            onClick = onPlay,
            shape = PillShape,
            contentPadding = padding,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = accent.container,
                contentColor = accent.onContainer,
            ),
            modifier = Modifier.heightIn(min = 48.dp).testTag("hub.tonal.$id"),
        ) { text() }
    }
}

/** What tapping a card does: a saved play continues, a solved day replays, an offline one retries. */
internal fun actionLabel(status: TodayStatus): String = when (status) {
    TodayStatus.NotStarted -> "Play"
    is TodayStatus.InProgress -> "Continue"
    is TodayStatus.Solved -> "Replay"
    TodayStatus.Offline -> "Try again"
}

@PuzzleBoxPreviews
@Composable
private fun HubCardPreview() {
    PuzzleBoxTheme {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            val zip = GameBrand.Neutral.copy(accent = AccentSlot.Blue)
            HubCard(
                entry = HubRow(GameId("zip"), "Zip", zip, 592, TodayStatus.NotStarted),
                best = 42_000L,
                isNext = true,
                onPlay = {},
            )
            HubCard(
                entry = HubRow(GameId("tango"), "Tango", status = TodayStatus.Offline),
                best = null,
                isNext = false,
                onPlay = {},
            )
        }
    }
}
