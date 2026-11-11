package com.ivandsky.puzzlebox.home

import android.content.res.Configuration
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.LocalShellActions
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.PlayerAvatar
import com.ivandsky.puzzlebox.gameui.WeekDay
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import java.time.LocalDate

// Previews of Today as the app builds it: summary rows under the streak,
// no account card, and (phone) the shell's avatar in the header through LocalShellActions.

private val V2Day = LocalDate.of(2026, 11, 5)

private fun v2Row(id: String, name: String, brand: GameBrand, status: TodayStatus) =
    HomeUiState.TodayRow(GameId(id), name, status, brand, RankOrder.TimeThenHints)

private val V2Zip = GameBrand(AccentSlot.Blue, GameGlyphs.Zip)
private val V2Tango = GameBrand(AccentSlot.Orange, GameGlyphs.Tango)
private val V2Queens = GameBrand(AccentSlot.Violet, GameGlyphs.Queens)

private val V2Rows = listOf(
    v2Row("zip", "Zip", V2Zip, TodayStatus.Solved(9_000L)),
    v2Row("tango", "Tango", V2Tango, TodayStatus.InProgress(192_000L)),
    v2Row("queens", "Queens", V2Queens, TodayStatus.NotStarted),
    v2Row("strands", "Strands", GameBrand(AccentSlot.Pink, GameGlyphs.Strands), TodayStatus.Solved(261_000L)),
    v2Row("sudoku", "Mini Sudoku", GameBrand(AccentSlot.Teal, GameGlyphs.Sudoku), TodayStatus.Solved(112_000L)),
)

private val V2History = listOf(
    HistoryDay(
        "2026-11-04",
        "Yesterday",
        listOf(HistoryDay.GameResult("Zip", V2Zip, "0:42"), HistoryDay.GameResult("Tango", V2Tango, "1:18")),
    ),
    HistoryDay("2026-11-03", "Mon, Nov 3", listOf(HistoryDay.GameResult("Queens", V2Queens, "2:05"))),
    HistoryDay("2026-11-02", "Sun, Nov 2", listOf(HistoryDay.GameResult("Zip", V2Zip, null))),
)

private val V2State = HomeUiState(
    today = V2Rows,
    identity = null,
    history = V2History,
    puzzleDay = V2Day,
    playTarget = GameId("tango"),
    stats = GameStats(4, 9, 31, 22, 96_000L, V2Day, best = null, rankOrder = RankOrder.TimeThenHints),
    week = (6L downTo 0L).map { back ->
        WeekDay(date = V2Day.minusDays(back), solved = V2Rows.take(((6 - back) % 3).toInt()).map { it.brand }, isToday = back == 0L)
    },
    rolloverAt = Instant.EPOCH,
    rank = TodayRank(GameId("zip"), "Zip", 3),
)

private val V2Summaries = HomeSummaries(onOpenRank = {}, duels = HomeDuelsSummary(onOpen = {}, needYou = { 2 }))

@Composable
private fun HomeToday(layout: HomeLayout) {
    PuzzleBoxTheme {
        // The phone's header carries the shell avatar; the tablet's rail owns it.
        val avatar: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit =
            if (layout == HomeLayout.SinglePane) {
                { IconButton(onClick = {}) { PlayerAvatar(name = "Ann", photoUrl = null, size = 28.dp) } }
            } else {
                {}
            }
        CompositionLocalProvider(LocalShellActions provides avatar) {
            Surface(color = MaterialTheme.colorScheme.background) {
                HomeScreen(
                    layout = layout,
                    state = V2State,
                    onOpenGame = {},
                    onOpenHistory = {},
                    onSelectDay = {},
                    title = "Today",
                    summaries = V2Summaries,
                )
            }
        }
    }
}

@Preview(name = "Light", widthDp = 360, heightDp = 1200, showBackground = true)
@Preview(name = "Dark", widthDp = 360, heightDp = 1200, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun HomeTodayPhonePreview() = HomeToday(HomeLayout.SinglePane)

@Preview(name = "Light", widthDp = 1280, heightDp = 800, showBackground = true)
@Preview(name = "Dark", widthDp = 1280, heightDp = 800, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun HomeTodayTabletPreview() = HomeToday(HomeLayout.TwoPane)
