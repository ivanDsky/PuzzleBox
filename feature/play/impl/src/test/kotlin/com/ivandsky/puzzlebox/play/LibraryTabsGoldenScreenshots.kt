package com.ivandsky.puzzlebox.play

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.ArchiveProgress
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameCardModel
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinApplication
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Games screen with a Duels slot, phone and tablet, light and dark: each of the three
 * tabs selected in turn, with a stand-in Duels body and a need-you count on its pill.
 * Goldens in `snapshots/tabs/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class LibraryTabsGoldenScreenshotsPhone : LibraryTabsGoldenScreenshots(PlayLayout.SinglePane, "phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class LibraryTabsGoldenScreenshotsTablet : LibraryTabsGoldenScreenshots(PlayLayout.TwoPane, "tablet")

abstract class LibraryTabsGoldenScreenshots(private val layout: PlayLayout, private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var koin: KoinApplication? = null

    /** The tablet's first game page carries the game's board, which a view model builds. */
    @Before
    fun startGraph() {
        koin = startGamePageBoardKoin(STATE.rows.map { it.card.game })
    }

    @After
    fun stopGraph() {
        koin?.close()
    }

    @Test fun `dailies light`() = capture("tabs-dailies", GamesTab.Dailies, dark = false)
    @Test fun `dailies dark`() = capture("tabs-dailies", GamesTab.Dailies, dark = true)

    @Test fun `friends light`() = capture("tabs-friends", GamesTab.Friends, dark = false)
    @Test fun `friends dark`() = capture("tabs-friends", GamesTab.Friends, dark = true)

    @Test fun `duels light`() = capture("tabs-duels", GamesTab.Duels, dark = false)
    @Test fun `duels dark`() = capture("tabs-duels", GamesTab.Duels, dark = true)

    private fun capture(name: String, tab: GamesTab, dark: Boolean) {
        rule.showGolden(dark) {
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
                onOpenGameHistory = { _, _ -> },
                friends = FriendPreviewCards,
                onEnterCode = {},
                duels = DUELS,
            )
        }
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        if (tab != GamesTab.Dailies) {
            rule.onNodeWithTag("library.tab.${tab.name.lowercase()}").performClick()
            rule.mainClock.advanceTimeBy(SETTLE_MS)
        }
        // The frame is the tab it names: the Duels golden is the Duels pill selected, not a stale Dailies.
        rule.onNodeWithTag("library.tab.${tab.name.lowercase()}").assertIsSelected()
        rule.settleAndCapture("tabs", name, device, dark)
    }

    private companion object {
        const val SETTLE_MS = 1_000L

        val TODAY: LocalDate = LocalDate.parse("2026-11-05")

        /** A stand-in for the compete module's tab: a few cards in a list of its own; the host owns the side inset. */
        val DUELS = DuelsSlot(
            content = {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    repeat(3) { index ->
                        Surface(Modifier.fillMaxWidth().height(96.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large) {
                            Text("Duel ${index + 1}", Modifier.padding(Spacing.lg))
                        }
                    }
                }
            },
            needYou = { 3 },
            onOpenDuel = {},
        )

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
