package com.ivandsky.puzzlebox.play.linkedin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.data.linkedin.LinkedInGhost
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInBoardView
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayStatus
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.play.libraryCardColors

private const val PREVIEW_NOW_MS = 1_800_000_000_000L
private const val FIVE_MINUTES_MS = 300_000L

private val PreviewGhosts = listOf(
    LinkedInGhost("m-1", "Ana Costa", 41_000L, hintFree = true, flawless = true),
    LinkedInGhost("m-2", "Jonas Lind", 63_000L, hintFree = true, flawless = false),
    LinkedInGhost("m-3", "Mei Tanaka", 88_000L, hintFree = false, flawless = false),
    LinkedInGhost("m-4", "Omar Haddad", null, hintFree = false, flawless = false),
)

private fun shown(yourRank: Int?, total: Int, ghosts: List<LinkedInGhost>) =
    LinkedInBoardView.Shown(ghosts, yourRank, total, PREVIEW_NOW_MS - FIVE_MINUTES_MS)

private fun status(status: LinkedInDayStatus, board: LinkedInBoardView = LinkedInBoardView.Hidden) = LinkedInDayView(status, board)

/** Each view on its own accent card, so the colours are the ones the day card gives. */
@Composable
private fun PreviewCards(vararg views: LinkedInDayView, accent: AccentSlot = AccentSlot.Blue) {
    PuzzleBoxTheme {
        val colors = libraryCardColors(GameBrand(accent, GameGlyphs.Zip))
        Column(Modifier.background(colors.fill).padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            views.forEach { view ->
                LinkedInDayLines(
                    view = view,
                    colors = colors,
                    nowMs = PREVIEW_NOW_MS,
                    onResume = {},
                    onReconnect = {},
                    onRetry = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// Preview: the board is on its way, a dashed ghost row of skeleton lines.
@PuzzleBoxPreviews
@Composable
internal fun LinkedInLoadingPreview() = PreviewCards(status(LinkedInDayStatus.Loading))

// Preview: the player's own result is imported, ranked among connections.
@PuzzleBoxPreviews
@Composable
internal fun LinkedInImportedRankedPreview() =
    PreviewCards(status(LinkedInDayStatus.Imported(52_000L), shown(yourRank = 2, total = 5, ghosts = PreviewGhosts)))

// Preview: not imported, so the summary says how many connections played,.
@PuzzleBoxPreviews
@Composable
internal fun LinkedInUnrankedListPreview() =
    PreviewCards(status(LinkedInDayStatus.DidNotPlay, shown(yourRank = null, total = 4, ghosts = PreviewGhosts)))

// Preview: connected, and nobody on the board.
@PuzzleBoxPreviews
@Composable
internal fun LinkedInEmptyBoardPreview() =
    PreviewCards(status(LinkedInDayStatus.DidNotPlay, shown(yourRank = null, total = 0, ghosts = emptyList())))

// Preview: a day older than LinkedIn keeps connections' boards.
@PuzzleBoxPreviews
@Composable
internal fun LinkedInOutsideWindowPreview() =
    PreviewCards(status(LinkedInDayStatus.Imported(52_000L), LinkedInBoardView.OutsideWindow))

// Preview: the player did not play this day on LinkedIn.
@PuzzleBoxPreviews
@Composable
internal fun LinkedInDidNotPlayPreview() = PreviewCards(status(LinkedInDayStatus.DidNotPlay))

// Preview: LinkedIn can't be reached, with Retry.
@PuzzleBoxPreviews
@Composable
internal fun LinkedInOfflinePreview() = PreviewCards(status(LinkedInDayStatus.Offline))

// Preview: the session ended, with Reconnect.
@PuzzleBoxPreviews
@Composable
internal fun LinkedInSessionExpiredPreview() = PreviewCards(status(LinkedInDayStatus.SessionExpired))

// Preview: paused by a LinkedIn check, with Resume.
@PuzzleBoxPreviews
@Composable
internal fun LinkedInPausedChallengePreview() = PreviewCards(status(LinkedInDayStatus.Paused(Kind.Challenge)))

// Preview: paused after LinkedIn asked to slow down, with Resume.
@PuzzleBoxPreviews
@Composable
internal fun LinkedInPausedRateLimitedPreview() = PreviewCards(status(LinkedInDayStatus.Paused(Kind.RateLimited)))

// Preview: paused after an answer PuzzleBox didn't expect, with Resume.
@PuzzleBoxPreviews
@Composable
internal fun LinkedInPausedUnexpectedPreview() = PreviewCards(status(LinkedInDayStatus.Paused(Kind.Unexpected)))

// Preview: paused because the session ended reads as the Reconnect row.
@PuzzleBoxPreviews
@Composable
internal fun LinkedInPausedSessionExpiredPreview() = PreviewCards(status(LinkedInDayStatus.Paused(Kind.SessionExpired)))

// Preview: the skeleton and a ranked list on the Orange accent (Tango).
@PuzzleBoxPreviews
@Composable
internal fun LinkedInOrangeAccentPreview() = PreviewCards(
    status(LinkedInDayStatus.Loading),
    status(LinkedInDayStatus.Imported(52_000L), shown(yourRank = 2, total = 5, ghosts = PreviewGhosts)),
    accent = AccentSlot.Orange,
)

// Preview: the skeleton and a ranked list on the Violet accent (Queens).
@PuzzleBoxPreviews
@Composable
internal fun LinkedInVioletAccentPreview() = PreviewCards(
    status(LinkedInDayStatus.Loading),
    status(LinkedInDayStatus.Imported(52_000L), shown(yourRank = 2, total = 5, ghosts = PreviewGhosts)),
    accent = AccentSlot.Violet,
)

// Preview: the skeleton and a ranked list on the Teal accent (Mini Sudoku).
@PuzzleBoxPreviews
@Composable
internal fun LinkedInTealAccentPreview() = PreviewCards(
    status(LinkedInDayStatus.Loading),
    status(LinkedInDayStatus.Imported(52_000L), shown(yourRank = 2, total = 5, ghosts = PreviewGhosts)),
    accent = AccentSlot.Teal,
)

// Preview: the skeleton and a paused read with its Resume pill over a ranked summary on the Pink accent (Strands).
@PuzzleBoxPreviews
@Composable
internal fun LinkedInPinkAccentPreview() = PreviewCards(
    status(LinkedInDayStatus.Loading),
    status(LinkedInDayStatus.Paused(Kind.Challenge), shown(yourRank = null, total = 4, ghosts = PreviewGhosts)),
    accent = AccentSlot.Pink,
)
