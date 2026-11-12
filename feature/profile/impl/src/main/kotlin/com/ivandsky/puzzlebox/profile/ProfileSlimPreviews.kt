package com.ivandsky.puzzlebox.profile

import androidx.compose.runtime.Composable
import com.ivandsky.puzzlebox.app.screens.ProfileContent
import com.ivandsky.puzzlebox.app.screens.ProfileUiState
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInAccountView
import com.ivandsky.puzzlebox.profile.accounts.LinkedInRowState
import java.time.LocalDate

private const val PREVIEW_NOW_MS = 1_800_000_000_000L
private const val PREVIEW_MINUTE_MS = 60_000L

private val SignedIn = ProfileUiState.Account.SignedIn(Identity(uid = "u1", displayName = "Ann", photoUrl = null))

private val KnownSummary = LinkedInAccountView(
    name = "Mira Voss-Halloran Åkerström",
    photoPath = null,
    connectionsSeen = 142,
    daysThisWeek = 5,
    daysTotal = 213,
    games = listOf("Queens", "Tango", "Zip"),
    updatedAtMs = PREVIEW_NOW_MS - 12 * PREVIEW_MINUTE_MS,
)

private val UnknownSummary = LinkedInAccountView(null, null, 0, 0, 0, emptyList(), null)

@Composable
private fun SlimProfile(
    account: ProfileUiState.Account,
    linkedIn: LinkedInRowState? = null,
    boards: ProfileUiState.LinkedInBoards? = null,
) {
    PuzzleBoxTheme {
        ProfileContent(
            state = ProfileUiState(account = account, playingSince = LocalDate.of(2026, 3, 1), linkedIn = linkedIn, linkedInBoards = boards),
            onSignIn = {},
            onSignOut = {},
            onBack = {},
            onOpenSettings = {},
            nowMs = PREVIEW_NOW_MS,
            onConnectLinkedIn = linkedIn?.let { {} },
        )
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ProfileSlimSignedInPreview() = SlimProfile(SignedIn)

@PuzzleBoxPreviews
@Composable
internal fun ProfileSlimSignedOutPreview() = SlimProfile(ProfileUiState.Account.SignedOut())

@PuzzleBoxPreviews
@Composable
internal fun ProfileLinkedInNotConnectedPreview() = SlimProfile(SignedIn, LinkedInRowState.NotConnected)

@PuzzleBoxPreviews
@Composable
internal fun ProfileLinkedInConnectedPreview() =
    SlimProfile(SignedIn, LinkedInRowState.Connected(KnownSummary))

@PuzzleBoxPreviews
@Composable
internal fun ProfileLinkedInNameUnknownPreview() =
    SlimProfile(ProfileUiState.Account.SignedOut(), LinkedInRowState.Connected(UnknownSummary))

@PuzzleBoxPreviews
@Composable
internal fun ProfileLinkedInPausedPreview() = SlimProfile(
    SignedIn,
    LinkedInRowState.Paused(KnownSummary, "LinkedIn is asking us to slow down, so reading is paused.", needsReconnect = false),
)

@PuzzleBoxPreviews
@Composable
internal fun ProfileLinkedInReconnectPreview() = SlimProfile(
    SignedIn,
    LinkedInRowState.Paused(KnownSummary, "Your LinkedIn login expired. Reconnect to keep reading.", needsReconnect = true),
)

@PuzzleBoxPreviews
@Composable
internal fun ProfileLinkedInSavedToAccountPreview() = SlimProfile(
    SignedIn,
    LinkedInRowState.NotConnected,
    ProfileUiState.LinkedInBoards(savedToAccount = true, canDelete = true),
)

@PuzzleBoxPreviews
@Composable
internal fun ProfileLinkedInSignInHintPreview() = SlimProfile(
    ProfileUiState.Account.SignedOut(),
    LinkedInRowState.Connected(KnownSummary),
    ProfileUiState.LinkedInBoards(savedToAccount = true, canDelete = true),
)

@PuzzleBoxPreviews
@Composable
internal fun ProfileLinkedInDeleteBoardsOfflinePreview() = SlimProfile(
    SignedIn,
    LinkedInRowState.Connected(KnownSummary),
    ProfileUiState.LinkedInBoards(
        savedToAccount = true,
        canDelete = true,
        deleteMessage = "You're offline. Try again when you're connected.",
    ),
)
