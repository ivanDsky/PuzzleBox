package com.ivandsky.puzzlebox.insights.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.insights.domain.stats.GameStanding
import com.ivandsky.puzzlebox.insights.domain.stats.SectionState
import com.ivandsky.puzzlebox.insights.domain.stats.VsFriendsStanding
import com.ivandsky.puzzlebox.model.GameId
import java.util.Locale

/**
 * The vs friends section: the game the viewer scores best in among friends, and the weakest when it differs, each with
 * its points and place. While it loads or when the read fails it is the usual titled frame with its quiet line (A16).
 * When there is nothing to compare yet (signed out, or no friends) the section is replaced by one plain line, with no
 * title, saying what unlocks it.
 */
@Composable
internal fun VsFriendsSection(state: SectionState<VsFriendsStanding>, gameName: (GameId) -> String, modifier: Modifier = Modifier) {
    val locked = (state as? SectionState.Ready)?.value as? VsFriendsStanding.Locked
    if (locked != null) {
        Text(
            text = locked.unlock.line(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.fillMaxWidth().testTag("stats.vsFriends.locked"),
        )
        return
    }
    StatsSectionFrame(title = "Vs friends", state = state, tag = "stats.vsFriends", modifier = modifier) { standing ->
        if (standing is VsFriendsStanding.Ranked) {
            Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                StandingRow("stats.vsFriends.best", "Best game", standing.best, gameName)
                standing.weakest?.let { weakest ->
                    HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = MaterialTheme.colorScheme.outlineVariant)
                    StandingRow("stats.vsFriends.weakest", "Weakest game", weakest, gameName)
                }
            }
        }
    }
}

private fun VsFriendsStanding.Unlock.line(): String = when (this) {
    VsFriendsStanding.Unlock.SignIn -> "Sign in and add friends to see how your games compare."
    VsFriendsStanding.Unlock.AddFriends -> "Add friends to see how your games compare."
}

@Composable
private fun StandingRow(tag: String, role: String, standing: GameStanding, gameName: (GameId) -> String) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .padding(horizontal = Spacing.lg)
            .semantics(mergeDescendants = true) {}
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(Modifier.weight(1f).padding(vertical = Spacing.sm)) {
            Text(text = gameName(standing.game), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = "$role · place ${standing.place} of ${standing.players}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = String.format(Locale.ROOT, "%,.0f pts", standing.points),
            style = MaterialTheme.typography.titleMedium.tabular(),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val RowMinHeight = 56.dp
