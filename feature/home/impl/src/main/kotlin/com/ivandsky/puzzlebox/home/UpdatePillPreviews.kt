package com.ivandsky.puzzlebox.home

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.WeekDay
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import java.time.LocalDate

// Previews for the Update available pill (ticket 07, spec A7/S7/S8): the pill alone, then
// the real Home header carrying it at the 360 dp phone floor and in the tablet TwoPane list
// pane, light + dark. UpdatePillScreenshotTest renders these same functions into goldens.
// No motion to scrub: the pill's only animation is the touch-driven press scale.

private val PillDay = LocalDate.of(2026, 11, 5)
private const val PREVIEW_URL = "https://github.com/ivanDsky/PuzzleBox/releases/latest"

private fun pillRow(id: String, name: String, brand: GameBrand, status: TodayStatus) =
    HomeUiState.TodayRow(GameId(id), name, status, brand, RankOrder.TimeThenHints)

private val PillRows = listOf(
    pillRow("zip", "Zip", GameBrand(AccentSlot.Blue, GameGlyphs.Zip), TodayStatus.Solved(9_000L)),
    pillRow("tango", "Tango", GameBrand(AccentSlot.Orange, GameGlyphs.Tango), TodayStatus.InProgress(192_000L)),
    pillRow("queens", "Queens", GameBrand(AccentSlot.Violet, GameGlyphs.Queens), TodayStatus.NotStarted),
    pillRow("strands", "Strands", GameBrand(AccentSlot.Pink, GameGlyphs.Strands), TodayStatus.Solved(261_000L)),
    pillRow("sudoku", "Mini Sudoku", GameBrand(AccentSlot.Teal, GameGlyphs.Sudoku), TodayStatus.NotStarted),
)

private val PillState = HomeUiState(
    today = PillRows,
    identity = null,
    history = emptyList(),
    puzzleDay = PillDay,
    playTarget = GameId("tango"),
    stats = GameStats(4, 9, 31, 22, 96_000L, PillDay, best = null, rankOrder = RankOrder.TimeThenHints),
    week = (6L downTo 0L).map { back ->
        WeekDay(date = PillDay.minusDays(back), solved = PillRows.take(((6 - back) % 3).toInt()).map { it.brand }, isToday = back == 0L)
    },
    rolloverAt = Instant.EPOCH,
    updateUrl = PREVIEW_URL,
)

@Composable
private fun HomeWithUpdate(layout: HomeLayout) {
    PuzzleBoxTheme {
        HomeScreen(
            layout = layout,
            state = PillState,
            onOpenGame = {},
            onOpenProfile = {},
        )
    }
}

// Preview: the pill alone on the app background.
@PuzzleBoxPreviews
@Composable
internal fun UpdatePillPreview() {
    PuzzleBoxTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.lg)) {
            UpdatePill(onClick = {})
        }
    }
}

// Preview: the single-pane header at the 360 dp phone floor — title and pill share one row.
@Preview(name = "Light", widthDp = 360, heightDp = 640, showBackground = true)
@Preview(name = "Dark", widthDp = 360, heightDp = 640, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun HomeUpdatePhonePreview() = HomeWithUpdate(HomeLayout.SinglePane)

// Preview: the tablet landscape TwoPane — the pill in the 42% list pane's header.
@Preview(name = "Light", widthDp = 1280, heightDp = 800, showBackground = true)
@Preview(name = "Dark", widthDp = 1280, heightDp = 800, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun HomeUpdateTabletPreview() = HomeWithUpdate(HomeLayout.TwoPane)
