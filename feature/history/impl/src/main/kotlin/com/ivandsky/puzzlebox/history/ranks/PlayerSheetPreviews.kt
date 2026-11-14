package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.runtime.Composable
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.domain.ranks.PlayerActions
import com.ivandsky.puzzlebox.domain.ranks.PlayerActions.Challenge
import com.ivandsky.puzzlebox.domain.ranks.PlayerActions.Watch
import com.ivandsky.puzzlebox.history.ranks.PlayerSheetViewModel.Challenge as ChallengeStatus
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

// The player sheet's states the open and sent previews beside it do not show, on a game's board (one game to duel).

private val Zip = PlayerSheetViewModel.Game(GameId("zip"), "Zip", PuzzleIcons.Play)

private val Target = PlayerSheetViewModel.Target("maya", "Maya", Standing.Place(2), game = Zip.id, day = LocalDate.of(2026, 11, 13))

@Composable
private fun Sheet(actions: PlayerActions, challenge: ChallengeStatus = ChallengeStatus.Idle, onWatch: (() -> Unit)? = null) {
    PuzzleBoxTheme {
        PlayerSheet(
            PlayerSheetViewModel.State(Target, actions, games = listOf(Zip), challenge = challenge),
            onChallenge = {},
            onDismiss = {},
            onWatch = onWatch,
        )
    }
}

@PuzzleBoxPreviews
@Composable
private fun PlayerSheetPrivatePreview() = Sheet(PlayerActions(Challenge.Private, Watch.Private))

@PuzzleBoxPreviews
@Composable
private fun PlayerSheetFailedPreview() = Sheet(PlayerActions(Challenge.Allowed, Watch.SolveFirst), ChallengeStatus.Failed("You're offline"))

@PuzzleBoxPreviews
@Composable
private fun PlayerSheetSendingPreview() = Sheet(PlayerActions(Challenge.Allowed, Watch.SolveFirst), ChallengeStatus.Sending)

@PuzzleBoxPreviews
@Composable
private fun PlayerSheetWatchPreview() = Sheet(PlayerActions(Challenge.Allowed, Watch.Allowed), onWatch = {})
