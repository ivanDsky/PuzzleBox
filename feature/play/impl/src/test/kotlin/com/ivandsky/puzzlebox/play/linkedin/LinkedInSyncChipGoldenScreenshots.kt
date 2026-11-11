package com.ivandsky.puzzlebox.play.linkedin

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.ArchiveDay
import com.ivandsky.puzzlebox.domain.ArchiveProgress
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.PastDayStatus
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayStatus
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameCardModel
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.ArchiveDayCard
import com.ivandsky.puzzlebox.play.GOLDEN_PHONE
import com.ivandsky.puzzlebox.play.LibraryRowModel
import com.ivandsky.puzzlebox.play.libraryCardColors
import com.ivandsky.puzzlebox.play.settleAndCapture
import com.ivandsky.puzzlebox.play.showGolden
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The day card's LinkedIn sync chip on the Blue accent card in each state, light, and once in dark; and the whole
 * day card of an archive day, whose "From the archive" pill is the widest thing beside the chip. Goldens in
 * `snapshots/linkedin/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class LinkedInSyncChipGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `loading light`() = chip("sync-chip-loading", dark = false, LinkedInDayStatus.Loading)
    @Test fun `synced light`() = chip("sync-chip-synced", dark = false, LinkedInDayStatus.Imported(52_000L, 0))
    @Test fun `paused light`() = chip("sync-chip-paused", dark = false, LinkedInDayStatus.Paused(Kind.Challenge))
    @Test fun `session expired light`() = chip("sync-chip-expired", dark = false, LinkedInDayStatus.SessionExpired)
    @Test fun `offline light`() = chip("sync-chip-offline", dark = false, LinkedInDayStatus.Offline)
    @Test fun `paused dark`() = chip("sync-chip-paused", dark = true, LinkedInDayStatus.Paused(Kind.Challenge))

    @Test fun `archive day card with the chip light`() = dayCard("sync-chip-day-card", dark = false)
    @Test fun `archive day card with the chip dark`() = dayCard("sync-chip-day-card", dark = true)

    private fun chip(name: String, dark: Boolean, status: LinkedInDayStatus) {
        rule.showGolden(dark) {
            val colors = libraryCardColors(GameBrand(AccentSlot.Blue, GameGlyphs.Zip))
            Box(Modifier.fillMaxWidth().background(colors.fill).padding(Spacing.lg), contentAlignment = Alignment.TopEnd) {
                LinkedInSyncChip(status, onResume = {}, onReconnect = {}, onRetry = {})
            }
        }
        rule.settleAndCapture("linkedin", name, "phone", dark)
    }

    private fun dayCard(name: String, dark: Boolean) {
        val today = LocalDate.parse("2026-11-05")
        val row = LibraryRowModel(
            card = GameCardModel(
                game = GameId("zip"),
                name = "Zip",
                brand = GameBrand(AccentSlot.Blue, GameGlyphs.Zip),
                status = TodayStatus.NotStarted,
                rankOrder = RankOrder.TimeThenHints,
                puzzleNumber = 10,
                best = null,
            ),
            today = 10,
            progress = ArchiveProgress(today.minusDays(9)..today, 10, 0, 0),
            stats = GameStats(0, 0, 0, 0, null, null, null, RankOrder.TimeThenHints),
            rules = emptyList(),
        )
        val past = ArchiveDay(today.minusDays(3), 7, PastDayStatus.Solved(41_000L), archived = true, openedOn = null)
        rule.showGolden(dark) {
            Box(Modifier.padding(Spacing.lg)) {
                ArchiveDayCard(
                    row, today, past, offline = false, onPlay = {}, onRetry = {}, onBackToToday = {},
                    linkedIn = { LinkedInSyncChip(LinkedInDayStatus.SessionExpired, onResume = {}, onReconnect = {}, onRetry = {}) },
                )
            }
        }
        rule.settleAndCapture("linkedin", name, "phone", dark)
    }
}
