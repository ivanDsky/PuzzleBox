package com.ivandsky.puzzlebox.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.app.screens.ProfileUiState
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** The top of Profile (A20): the player's [name], then "Playing since" once they have a first solve. */
@Composable
internal fun ProfileHeader(name: String, playingSince: LocalDate?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().testTag("profile.header"),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (playingSince != null) {
            Text(
                text = "Playing since ${playingSince.format(SINCE_FORMAT)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("profile.playing_since"),
            )
        }
    }
}

/** The lifetime numbers across every game (A20): solved, best streak, no hints, days played. */
@Composable
internal fun LifetimeGrid(stats: GameStats, daysPlayed: Int, modifier: Modifier = Modifier) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth().testTag("profile.stats"),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatTile("${stats.solved}", "Solved", "profile.stats.solved", Modifier.weight(1f))
                StatTile("${stats.bestStreak}", "Best streak", "profile.stats.best_streak", Modifier.weight(1f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatTile("${stats.hintFree}", "No hints", "profile.stats.hint_free", Modifier.weight(1f))
                StatTile("$daysPlayed", "Days played", "profile.stats.days_played", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, tag: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.tabular(),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag(tag),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One row per game (A20, tag `profile.game.<gameId>`): its glyph, name and solved count,
 * then its best time (`profile.game.best.<gameId>`). A tap opens that game's History tab (N3).
 */
@Composable
internal fun GameRows(games: List<ProfileUiState.GameRow>, onOpenHistory: (GameId) -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth().testTag("profile.games"),
        shape = MaterialTheme.shapes.large,
    ) {
        games.forEachIndexed { index, game ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            GameRow(game, onClick = { onOpenHistory(game.gameId) })
        }
    }
}

@Composable
private fun GameRow(game: ProfileUiState.GameRow, onClick: () -> Unit) {
    val id = game.gameId.value
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("profile.game.$id")
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameGlyph(game.brand, GameGlyphSize.Tile)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = game.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${game.solved} solved",
                style = MaterialTheme.typography.bodySmall.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("profile.game.solved.$id"),
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = game.bestTimeMs?.let(::formatDuration) ?: "–",
                style = MaterialTheme.typography.titleMedium.tabular(),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("profile.game.best.$id"),
            )
            Text(
                text = "Best",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = PuzzleIcons.Chevron,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val SINCE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
