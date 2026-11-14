package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.ranks.PlayerActions
import com.ivandsky.puzzlebox.domain.ranks.PlayerActions.Challenge
import com.ivandsky.puzzlebox.domain.ranks.PlayerActions.Watch
import com.ivandsky.puzzlebox.history.ranks.PlayerSheetViewModel.Challenge as ChallengeStatus
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

private val TILE_ICON = 28.dp
private val GAME_ICON = 24.dp
private val TILE_WIDTH = 120.dp

/**
 * The sheet a ranked row opens, icon first: the player's name and place over the two things to do with them. Challenge
 * (one tap on a game's board, a row of game icons to pick from on Overall) ends on "Sent to <name>"; Watch shows
 * why it is locked when it is. [onWatch] null leaves the Watch tile disabled. [onDismiss] closes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerSheet(
    state: PlayerSheetViewModel.State,
    onChallenge: (GameId) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onWatch: (() -> Unit)? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier.testTag("ranks.player.sheet")) {
        PlayerSheetContent(state, onChallenge, onWatch)
    }
}

@Composable
internal fun PlayerSheetContent(state: PlayerSheetViewModel.State, onChallenge: (GameId) -> Unit, onWatch: (() -> Unit)?) {
    val actions = state.actions
    Column(
        Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(state.target.name.ifEmpty { "Player" }, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("ranks.player.name"))
        Text(
            state.target.place.label(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { contentDescription = state.target.place.describe() }.testTag("ranks.player.place"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl), verticalAlignment = Alignment.Top) {
            ChallengeTile(actions?.challenge, state, onChallenge)
            if (actions?.watch != Watch.Unavailable) WatchTile(actions?.watch, onWatch)
        }
        if (actions?.challenge == Challenge.Allowed && state.games.size > 1 && state.challenge !is ChallengeStatus.Sent) {
            GamePicker(state.games, enabled = state.challenge !is ChallengeStatus.Sending, onChallenge)
        }
    }
}

@Composable
private fun ChallengeTile(challenge: Challenge?, state: PlayerSheetViewModel.State, onChallenge: (GameId) -> Unit) {
    val single = state.games.singleOrNull()
    val allowed = challenge == Challenge.Allowed
    val sent = state.challenge as? ChallengeStatus.Sent
    val caption = when {
        challenge == null -> ""
        sent != null -> "Sent to ${sent.name}"
        !allowed -> "Private"
        state.challenge is ChallengeStatus.Sending -> "Sending…"
        state.challenge is ChallengeStatus.Failed -> state.challenge.message
        single == null -> "Pick a game"
        else -> "Challenge"
    }
    ActionTile(
        icon = if (sent != null) PuzzleIcons.Check else if (challenge == Challenge.Private) PuzzleIcons.Lock else PuzzleIcons.Trophy,
        label = "Challenge",
        caption = caption,
        enabled = allowed && single != null && state.challenge !is ChallengeStatus.Sending && sent == null,
        onClick = { single?.let { onChallenge(it.id) } },
        tag = "ranks.player.challenge",
    )
}

@Composable
private fun WatchTile(watch: Watch?, onWatch: (() -> Unit)?) {
    val caption = when (watch) {
        null -> ""
        Watch.Allowed -> "Watch"
        Watch.Private -> "Private"
        Watch.SolveFirst -> "Solve it first"
        Watch.Unavailable -> ""
    }
    ActionTile(
        icon = if (watch == Watch.Private || watch == Watch.SolveFirst) PuzzleIcons.Lock else PuzzleIcons.Play,
        label = "Watch",
        caption = caption,
        enabled = watch == Watch.Allowed && onWatch != null,
        onClick = { onWatch?.invoke() },
        tag = "ranks.player.watch",
    )
}

/** A round icon button over its [caption]: [label] is what a screen reader names it, and [caption] says its state. */
@Composable
private fun ActionTile(icon: ImageVector, label: String, caption: String, enabled: Boolean, onClick: () -> Unit, tag: String) {
    Column(
        Modifier.widthIn(max = TILE_WIDTH).testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        FilledTonalIconButton(onClick, enabled = enabled, modifier = Modifier.size(64.dp)) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(TILE_ICON))
        }
        Text(
            caption,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("$tag.caption"),
        )
    }
}

/** Overall has no game of its own: one icon per game, a tap sends that game's duel. */
@Composable
private fun GamePicker(games: List<PlayerSheetViewModel.Game>, enabled: Boolean, onPick: (GameId) -> Unit) {
    Row(Modifier.testTag("ranks.player.games"), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        games.forEach { game ->
            FilledTonalIconButton({ onPick(game.id) }, enabled = enabled, modifier = Modifier.testTag("ranks.player.game.${game.id.value}")) {
                Icon(game.icon, contentDescription = "Challenge to ${game.name}", modifier = Modifier.size(GAME_ICON))
            }
        }
    }
}

private val PreviewTarget = PlayerSheetViewModel.Target("maya", "Maya", Standing.Place(2), game = null, day = LocalDate.of(2026, 11, 13))

@PuzzleBoxPreviews
@Composable
private fun PlayerSheetOpenPreview() {
    RanksPreviewHost {
        PlayerSheetContent(
            PlayerSheetViewModel.State(
                PreviewTarget,
                PlayerActions(Challenge.Allowed, Watch.SolveFirst),
                games = listOf(PlayerSheetViewModel.Game(GameId("zip"), "Zip", PuzzleIcons.Play), PlayerSheetViewModel.Game(GameId("tango"), "Tango", PuzzleIcons.Dice)),
            ),
            onChallenge = {},
            onWatch = null,
        )
    }
}

@PuzzleBoxPreviews
@Composable
private fun PlayerSheetSentPreview() {
    RanksPreviewHost {
        PlayerSheetContent(
            PlayerSheetViewModel.State(PreviewTarget, PlayerActions(Challenge.Private, Watch.Unavailable), challenge = ChallengeStatus.Sent("Maya")),
            onChallenge = {},
            onWatch = null,
        )
    }
}
