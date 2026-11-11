package com.ivandsky.puzzlebox.play.linkedin

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.ivandsky.puzzlebox.data.linkedin.LinkedInGhost
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInBoardView
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayStatus
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.play.GOLDEN_PHONE
import com.ivandsky.puzzlebox.play.libraryCardColors
import com.ivandsky.puzzlebox.play.settleAndCapture
import com.ivandsky.puzzlebox.play.showGolden
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The day card's LinkedIn lines on an accent card, light and dark: an imported result with a
 * ranked ghost list, the loading skeleton, a paused read and an ended session.
 * Goldens in `snapshots/linkedin/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class LinkedInDayLinesGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `imported with ghosts light`() = capture("day-lines-imported", dark = false, IMPORTED)
    @Test fun `imported with ghosts dark`() = capture("day-lines-imported", dark = true, IMPORTED)

    @Test fun `loading light`() = capture("day-lines-loading", dark = false, LinkedInDayView(LinkedInDayStatus.Loading, LinkedInBoardView.Hidden))
    @Test fun `loading dark`() = capture("day-lines-loading", dark = true, LinkedInDayView(LinkedInDayStatus.Loading, LinkedInBoardView.Hidden))

    @Test fun `paused light`() = capture("day-lines-paused", dark = false, PAUSED)
    @Test fun `paused dark`() = capture("day-lines-paused", dark = true, PAUSED)

    @Test fun `session expired light`() = capture("day-lines-expired", dark = false, EXPIRED)
    @Test fun `session expired dark`() = capture("day-lines-expired", dark = true, EXPIRED)

    private fun capture(name: String, dark: Boolean, view: LinkedInDayView) {
        rule.showGolden(dark) {
            val colors = libraryCardColors(GameBrand(AccentSlot.Blue, GameGlyphs.Zip))
            Box(Modifier.fillMaxWidth().background(colors.fill).padding(Spacing.lg), contentAlignment = Alignment.TopStart) {
                LinkedInDayLines(
                    view = view,
                    order = RankOrder.TimeThenHints,
                    colors = colors,
                    nowMs = NOW,
                    onResume = {},
                    onReconnect = {},
                    onRetry = {},
                )
            }
        }
        rule.settleAndCapture("linkedin", name, "phone", dark)
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val FIVE_MINUTES_MS = 300_000L

        val GHOSTS = listOf(
            LinkedInGhost("m-1", "Ana Costa", 41_000L, hintFree = true, flawless = true),
            LinkedInGhost("m-2", "Jonas Lind", 63_000L, hintFree = true, flawless = false),
            LinkedInGhost("m-3", "Mei Tanaka", 88_000L, hintFree = false, flawless = false),
            LinkedInGhost("m-4", "Omar Haddad", null, hintFree = false, flawless = false),
        )

        val IMPORTED = LinkedInDayView(
            LinkedInDayStatus.Imported(52_000L),
            LinkedInBoardView.Shown(GHOSTS, yourRank = 2, total = 5, updatedAtMs = NOW - FIVE_MINUTES_MS, listGhosts = true),
        )
        val PAUSED = LinkedInDayView(LinkedInDayStatus.Paused(Kind.Challenge), LinkedInBoardView.Hidden)
        val EXPIRED = LinkedInDayView(LinkedInDayStatus.SessionExpired, LinkedInBoardView.Hidden)
    }
}
