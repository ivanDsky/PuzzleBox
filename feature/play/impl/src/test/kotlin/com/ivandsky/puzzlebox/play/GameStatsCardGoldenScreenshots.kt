package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.play.domain.ArchiveProgress
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.resultui.GameCardModel
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The game's stats card with an archive progress: the archive's solves read as a plain sentence
 * under a divider, not as a truncated label row. Light and dark, phone and tablet.
 * Goldens in `snapshots/stats/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class GameStatsCardGoldenScreenshotsPhone : GameStatsCardGoldenScreenshots("phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class GameStatsCardGoldenScreenshotsTablet : GameStatsCardGoldenScreenshots("tablet")

abstract class GameStatsCardGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule: ComposeContentTestRule = createComposeRule()

    @Test fun `archive progress light`() = capture(dark = false)
    @Test fun `archive progress dark`() = capture(dark = true)

    private fun capture(dark: Boolean) {
        rule.showGolden(dark) {
            Column(Modifier.fillMaxWidth().padding(Spacing.md)) {
                GameStatsCard(ARCHIVE_ROW)
            }
        }
        rule.settleAndCapture("stats", "archive-progress", device, dark)
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.parse("2026-11-05")
        val ZIP = GameId("zip")
        val ORDER = RankOrder.TimeThenHints

        /** Twelve archive solves: the sentence as the card shows it, counting toward best but not streak. */
        val ARCHIVE_ROW = LibraryRowModel(
            card = GameCardModel(ZIP, "Zip", GameBrand.Neutral, status = null, rankOrder = ORDER),
            today = 41,
            progress = ArchiveProgress(range = TODAY.minusDays(9)..TODAY, total = 41, solved = 12, archivedSolves = 12),
            stats = GameStats(1, 1, 12, 12, 30_000L, TODAY, BestScore(30_000L, 0), ORDER),
            rules = gameRules(ZIP),
        )
    }
}
