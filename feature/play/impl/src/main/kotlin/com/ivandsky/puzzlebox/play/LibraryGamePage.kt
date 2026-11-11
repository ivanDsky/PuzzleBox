package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.linkedin.LinkedInCardSlot
import com.ivandsky.puzzlebox.play.linkedin.LinkedInCardState
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.launch

/**
 * [row]'s page in the library: its day card and its month calendar. [page] is the selected
 * game's calendar (another game's is ignored) and [month] the one it asked for (null = today's);
 * on the phone a picked day scrolls the page up to the day card. [onBack] is the phone's back row.
 * The page carries the game's leaderboard and a "Your history" row calling [onOpenGameHistory].
 * A non-null [linkedIn] puts the LinkedIn lines on the day card, which collects them while it is shown.
 */
@Composable
internal fun LibraryGamePage(
    row: LibraryRowModel,
    page: ArchivePageState?,
    month: YearMonth?,
    layout: PlayLayout,
    calendar: CalendarActions,
    onPlay: (GameId, LocalDate?) -> Unit,
    onRetry: () -> Unit,
    onOpenLesson: (GameId) -> Unit,
    onBack: (() -> Unit)?,
    offline: Boolean,
    hasLesson: Boolean,
    onOpenGameHistory: () -> Unit,
    linkedIn: LinkedInCardState? = null,
) {
    val game = row.card.game
    val shown = page?.takeIf { it.game == game }
    val past = shown?.picked
    // This game's source, not the library's: another game's failed today says nothing about it.
    val unreachable = row.card.status == TodayStatus.Offline
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val reduced = rememberReducedMotion()
    GamePage(
        row = row,
        layout = layout,
        dayCard = {
            ArchiveDayCard(
                row = row,
                today = row.progress?.range?.endInclusive,
                past = past,
                offline = unreachable,
                onPlay = { onPlay(game, past?.date) },
                onRetry = onRetry,
                onBackToToday = calendar.onBackToToday,
                linkedIn = linkedIn?.let { card -> { LinkedInCardSlot(card, row) } },
            )
        },
        calendar = {
            val range = row.progress?.range
            if (range != null) {
                val shownMonth = month ?: YearMonth.from(range.endInclusive)
                ArchiveMonthCalendar(
                    range = range,
                    month = shownMonth,
                    // Until the month's days load, its discs draw as outside rather than the old month's.
                    days = shown?.takeIf { it.month == shownMonth }?.days.orEmpty(),
                    picked = past?.date,
                    offline = unreachable,
                    accent = libraryCardColors(row.card.brand).fill,
                    compact = layout == PlayLayout.SinglePane,
                    onMonth = calendar.onShowMonth,
                    onPick = { day ->
                        calendar.onPick(day)
                        if (layout == PlayLayout.SinglePane) {
                            scope.launch { if (reduced) scroll.scrollTo(0) else scroll.animateScrollTo(0) }
                        }
                    },
                    modifier = if (layout == PlayLayout.TwoPane) Modifier.fillMaxHeight() else Modifier,
                )
            }
        },
        onBack = onBack,
        onOpenLesson = { onOpenLesson(game) },
        offline = offline,
        hasLesson = hasLesson,
        scroll = scroll,
        // The board shows the day the day card shows: the picked day, else the card's own latest day.
        board = { GamePageBoard(game, linkedIn = linkedIn != null, picked = past?.date, cardDay = row.cardDay) },
        onOpenHistory = onOpenGameHistory,
    )
}
