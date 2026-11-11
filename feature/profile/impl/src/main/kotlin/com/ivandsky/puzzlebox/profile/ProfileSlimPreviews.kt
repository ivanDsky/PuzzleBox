package com.ivandsky.puzzlebox.profile

import androidx.compose.runtime.Composable
import com.ivandsky.puzzlebox.app.screens.ProfileContent
import com.ivandsky.puzzlebox.app.screens.ProfileUiState
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.model.ThemeMode
import com.ivandsky.puzzlebox.profile.board.PerGameBoard
import java.time.LocalDate

// The slim Profile as the app builds it when NavV2 is on: back arrow and gear in the bar,
// the header and the account row only (stats, bests and appearance live elsewhere).

@Composable
private fun SlimProfile(account: ProfileUiState.Account) {
    PuzzleBoxTheme {
        ProfileContent(
            state = ProfileUiState(
                account = account,
                stats = ProfileUiState.NO_STATS.copy(playingSince = LocalDate.of(2026, 3, 1)),
                daysPlayed = 21,
                games = emptyList(),
                themeMode = ThemeMode.DARK,
                perGameBoard = PerGameBoard(null, RankOrder.TimeThenHints, DailyBoardState.Unavailable),
            ),
            onSignIn = {},
            onSignOut = {},
            onSetThemeMode = {},
            onOpenHistory = {},
            onSelectGame = {},
            onOpenCatalog = null,
            slim = true,
            onBack = {},
            onOpenSettings = {},
        )
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ProfileSlimSignedInPreview() =
    SlimProfile(ProfileUiState.Account.SignedIn(Identity(uid = "u1", displayName = "Ann", photoUrl = null)))

@PuzzleBoxPreviews
@Composable
internal fun ProfileSlimSignedOutPreview() = SlimProfile(ProfileUiState.Account.SignedOut())
