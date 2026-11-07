package com.ivandsky.puzzlebox.play

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.domain.ArchiveProgress
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameCardModel
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The library's From friends section, phone and tablet, light and dark: the friend cards in
 * each status scrolled into view, a card's Remove menu after a long press, and the Undo
 * snackbar after a remove. Goldens in `snapshots/friend/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class FromFriendsGoldenScreenshotsPhone : FromFriendsGoldenScreenshots(PlayLayout.SinglePane, "phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class FromFriendsGoldenScreenshotsTablet : FromFriendsGoldenScreenshots(PlayLayout.TwoPane, "tablet")

abstract class FromFriendsGoldenScreenshots(private val layout: PlayLayout, private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `section light`() = capture("library-section", dark = false)
    @Test fun `section dark`() = capture("library-section", dark = true)

    @Test fun `remove menu light`() = capture("library-remove-menu", dark = false) { openRemove() }
    @Test fun `remove menu dark`() = capture("library-remove-menu", dark = true) { openRemove() }

    @Test fun `undo snackbar light`() = capture("library-undo", dark = false, removed = CODE)
    @Test fun `undo snackbar dark`() = capture("library-undo", dark = true, removed = CODE)

    private fun capture(name: String, dark: Boolean, removed: ShareCode? = null, then: () -> Unit = {}) {
        rule.showGolden(dark) {
            Box(Modifier.fillMaxSize()) {
                LibraryScreen(
                    state = STATE,
                    page = null,
                    month = null,
                    layout = layout,
                    hasLesson = { true },
                    onSelect = {},
                    onPlay = { _, _ -> },
                    onRetry = {},
                    onOpenLesson = {},
                    calendar = CalendarActions(),
                    friends = FriendPreviewCards,
                    // The goldens show the shipped-on header: Enter code beside the title.
                    onEnterCode = {},
                )
                FriendRemovedSnackbar(removed = removed, onUndo = {}, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        rule.onNodeWithTag(ROW).performScrollTo()
        then()
        rule.settleAndCapture("friend", name, device, dark)
    }

    private fun openRemove() {
        rule.onNodeWithTag(ROW).performTouchInput { longClick() }
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        rule.onNodeWithTag("library.friends.${CODE.value}.remove").assertExists()
    }

    private companion object {
        const val SETTLE_MS = 1_000L

        val CODE = checkNotNull(ShareCode.parse("K7Q2MX"))
        val ROW = "library.friends.row.${CODE.value}"
        val TODAY: LocalDate = LocalDate.parse("2026-11-05")

        fun row(id: String, name: String, brand: GameBrand, status: TodayStatus?, total: Int, solved: Int): LibraryRowModel {
            val start = TODAY.minusDays(total - 1L)
            val stats = GameStats(4, 21, solved, solved * 8 / 9, 45_000L, start.plusDays(3), BestScore(16_000L, 0), RankOrder.TimeThenHints)
            return LibraryRowModel(
                card = GameCardModel(
                    game = GameId(id),
                    name = name,
                    brand = brand,
                    status = status,
                    rankOrder = RankOrder.TimeThenHints,
                    puzzleNumber = total,
                    best = stats.best,
                ),
                today = total,
                progress = ArchiveProgress(start..TODAY, total, solved, solved / 10),
                stats = stats,
                rules = gameRules(GameId(id)),
            )
        }

        val STATE: LibraryUiState = listOf(
            row("zip", "Zip", GameBrand(AccentSlot.Blue, GameGlyphs.Zip), TodayStatus.Solved(38_000L), 598, 531),
            row("tango", "Tango", GameBrand(AccentSlot.Orange, GameGlyphs.Tango), TodayStatus.InProgress(72_000L), 431, 240),
            row("queens", "Queens", GameBrand(AccentSlot.Violet, GameGlyphs.Queens), TodayStatus.NotStarted, 571, 112),
        ).let { rows -> LibraryUiState(rows, rows.first().card.game, offline = false) }
    }
}
