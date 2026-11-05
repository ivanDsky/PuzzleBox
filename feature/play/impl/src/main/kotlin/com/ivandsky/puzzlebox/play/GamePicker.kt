package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AppTextButton
import com.ivandsky.puzzlebox.designsystem.LocalPrimaryText
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import java.util.Locale

/**
 * The games hub's list (tag `route.hub`, spec A14): one card per playable game
 * (`hub.row.<gameId>`), in the order given, with today's status — "Not started",
 * "In progress · m:ss" (a saved play, as a tonal tag: the A13 cue) or "Solved · m:ss ✓" —
 * and what a tap does (Play, Continue, Replay) in the primary-text colour before the
 * chevron. No status line until the status is known. Picking a card is [onPick]. A card
 * with an archive also offers "Past puzzles" (`hub.past.<gameId>`, A22), which opens
 * [PastPuzzlesPicker] over its range; opening a day there is [onPickDay]. Which game's
 * picker is open is the only state held here.
 */
@Composable
internal fun GamePicker(
    rows: List<HubRow>,
    onPick: (GameId) -> Unit,
    onPickDay: (GameId, LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Home's card pairing (HomeScreen): cards float ABOVE the shell's ground in both
    // themes — white on cool paper in light, a lighter slate on near-black in dark.
    val scheme = MaterialTheme.colorScheme
    val cardsFloatLighter = scheme.surface.luminance() > scheme.surfaceContainerHighest.luminance()
    val cardColor = if (cardsFloatLighter) scheme.surface else scheme.surfaceContainerLow
    var pastFor by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        modifier
            .fillMaxWidth()
            .testTag("route.hub")
            .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = "Pick a game",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        rows.forEach { row -> HubCard(row, cardColor, onPick, onPast = { pastFor = row.game.value }) }
    }
    val past = rows.firstOrNull { it.game.value == pastFor }
    past?.archive?.let { range ->
        PastPuzzlesPicker(
            range = range,
            onOpen = { day ->
                pastFor = null
                onPickDay(past.game, day)
            },
            onDismiss = { pastFor = null },
        )
    }
}

@Composable
private fun HubCard(
    row: HubRow,
    color: androidx.compose.ui.graphics.Color,
    onPick: (GameId) -> Unit,
    onPast: () -> Unit,
) {
    val action = row.status?.let(::actionLabel)
    Surface(
        // Surface's own onClick clips the ripple to the card's rounded shape.
        onClick = { onPick(row.game) },
        shape = MaterialTheme.shapes.large,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hub.row.${row.game.value}")
            // TalkBack names what the tap does ("Continue"), not just "double-tap to activate".
            .semantics { if (action != null) onClick(label = action) { onPick(row.game); true } }
            // §9 touch floors: hub rows clear 48 dp with the padded content.
            .heightIn(min = 56.dp),
    ) {
        Row(
            // The chevron's 24 dp box carries its own optical inset: a tighter end pad.
            Modifier.padding(start = Spacing.lg, end = Spacing.md, top = Spacing.md, bottom = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                row.status?.let { status -> StatusLine(status, tag = "hub.status.${row.game.value}") }
                if (row.archive != null) PastPuzzlesAction(row, onPast)
            }
            action?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = LocalPrimaryText.current,
                    modifier = Modifier.testTag("hub.action.${row.game.value}"),
                )
            }
            // Decorative: the whole card is the click target and reads its name.
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The card's secondary action. Its own click target inside the card's, so a tap opens the
 * picker rather than today's play; TalkBack names the game it browses.
 */
@Composable
private fun PastPuzzlesAction(row: HubRow, onClick: () -> Unit) {
    // Pulled back by the button's inner start padding so its label lines up with the name.
    val inset = ButtonDefaults.TextButtonContentPadding.calculateStartPadding(LocalLayoutDirection.current)
    AppTextButton(
        text = "Past puzzles",
        onClick = onClick,
        modifier = Modifier
            .offset(x = -inset)
            .testTag("hub.past.${row.game.value}")
            .semantics { contentDescription = "Past ${row.name} puzzles" },
    )
}

/** The status line; in progress is the A13 cue — a small tonal tag on the secondary pair. */
@Composable
private fun StatusLine(status: TodayStatus, tag: String) {
    if (status is TodayStatus.InProgress) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Text(
                text = statusLine(status),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.testTag(tag).padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            )
        }
    } else {
        Text(
            text = statusLine(status),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(tag),
        )
    }
}

/** A row's status copy: the clock reads m:ss (0:42, 12:05). */
internal fun statusLine(status: TodayStatus): String = when (status) {
    TodayStatus.NotStarted -> "Not started"
    is TodayStatus.InProgress -> "In progress · ${minutesSeconds(status.elapsedMs)}"
    is TodayStatus.Solved -> "Solved · ${minutesSeconds(status.timeMs)} ✓"
}

/** What tapping a row does: a saved play continues, a solved day replays. */
internal fun actionLabel(status: TodayStatus): String = when (status) {
    TodayStatus.NotStarted -> "Play"
    is TodayStatus.InProgress -> "Continue"
    is TodayStatus.Solved -> "Replay"
}

private fun minutesSeconds(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000
    return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}

@PuzzleBoxPreviews
@Composable
private fun GamePickerPreview() {
    PuzzleBoxTheme {
        GamePicker(
            rows = listOf(
                HubRow(GameId("zip"), "Zip", TodayStatus.Solved(36_000L)),
                HubRow(GameId("tango"), "Tango", TodayStatus.InProgress(42_000L)),
                HubRow(GameId("queens"), "Queens", TodayStatus.NotStarted),
                HubRow(
                    GameId("strands"),
                    "Strands",
                    TodayStatus.NotStarted,
                    archive = LocalDate.of(2024, 3, 4)..LocalDate.of(2026, 11, 4),
                ),
                HubRow(GameId("pinpoint"), "Pinpoint", status = null),
            ),
            onPick = {},
            onPickDay = { _, _ -> },
        )
    }
}
