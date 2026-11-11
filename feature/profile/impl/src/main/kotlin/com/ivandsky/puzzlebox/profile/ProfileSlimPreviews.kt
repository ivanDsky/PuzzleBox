package com.ivandsky.puzzlebox.profile

import androidx.compose.runtime.Composable
import com.ivandsky.puzzlebox.app.screens.ProfileContent
import com.ivandsky.puzzlebox.app.screens.ProfileUiState
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import java.time.LocalDate

@Composable
private fun SlimProfile(account: ProfileUiState.Account) {
    PuzzleBoxTheme {
        ProfileContent(
            state = ProfileUiState(account = account, playingSince = LocalDate.of(2026, 3, 1)),
            onSignIn = {},
            onSignOut = {},
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
