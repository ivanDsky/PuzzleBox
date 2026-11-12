package com.ivandsky.puzzlebox.play.linkedin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayStatus
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.play.libraryCardColors

/** Each state on the Blue accent card, so the chip sits on the fill the day card gives it. */
@Composable
private fun PreviewChips(vararg states: LinkedInDayStatus) {
    PuzzleBoxTheme {
        val colors = libraryCardColors(GameBrand(AccentSlot.Blue, GameGlyphs.Zip))
        Column(Modifier.background(colors.fill).padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            states.forEach { LinkedInSyncChip(it, onResume = {}, onReconnect = {}, onRetry = {}) }
        }
    }
}

// Preview: the first read, with nothing settled to show yet: the logo and a ring, and no tap.
@PuzzleBoxPreviews
@Composable
internal fun LinkedInSyncChipLoadingPreview() = PreviewChips(LinkedInDayStatus.Loading)

// Preview: a read that outlasted its first second over a settled day: the ring in the check's place, same chip size.
@PuzzleBoxPreviews
@Composable
internal fun LinkedInSyncChipReadingPreview() {
    PuzzleBoxTheme {
        val colors = libraryCardColors(GameBrand(AccentSlot.Blue, GameGlyphs.Zip))
        Column(Modifier.background(colors.fill).padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            LinkedInSyncChip(LinkedInDayStatus.Imported(52_000L, 0), onResume = {}, onReconnect = {}, onRetry = {})
            LinkedInSyncChip(LinkedInDayStatus.Imported(52_000L, 0), reading = true, onResume = {}, onReconnect = {}, onRetry = {})
        }
    }
}

// Preview: the day is in sync (imported, not played on LinkedIn, or idle): the logo and a check.
@PuzzleBoxPreviews
@Composable
internal fun LinkedInSyncChipSyncedPreview() =
    PreviewChips(LinkedInDayStatus.Imported(52_000L, 0), LinkedInDayStatus.DidNotPlay, LinkedInDayStatus.Idle)

// Preview: LinkedIn stopped the sync, so the chip offers Resume.
@PuzzleBoxPreviews
@Composable
internal fun LinkedInSyncChipPausedPreview() = PreviewChips(LinkedInDayStatus.Paused(Kind.Challenge))

// Preview: the session ended, so the chip offers to sign in again.
@PuzzleBoxPreviews
@Composable
internal fun LinkedInSyncChipExpiredPreview() = PreviewChips(LinkedInDayStatus.SessionExpired)

// Preview: LinkedIn could not be reached, so the chip offers Retry.
@PuzzleBoxPreviews
@Composable
internal fun LinkedInSyncChipOfflinePreview() = PreviewChips(LinkedInDayStatus.Offline)
