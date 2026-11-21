package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.colors
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
 * The page carries the game's rank as one "You #12 of 340" row calling [onOpenRanks] with the game and
 * the day the page shows (the picked day, else the card's own).
 * A non-null [linkedIn] puts the LinkedIn sync chip on the day card, which collects its state while it is shown.
 * A non-null [onMakeOne] ends How to play with "Make your own <Game>", handing back this page's game.
 * A non-null [onOpenStats] puts "See all stats" on the stats card, handing back this page's game.
 * A non-null [onRandom] puts a "Random" chip at the right edge of the calendar's jump row for a game with an archive, handing back this page's game.
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
    onOpenRanks: (GameId, LocalDate?) -> Unit,
    linkedIn: LinkedInCardState? = null,
    onMakeOne: ((GameId) -> Unit)? = null,
    onRandom: ((GameId) -> Unit)? = null,
    onOpenStats: ((GameId) -> Unit)? = null,
) {
    val game = row.card.game
    val linkedInCard = linkedIn?.takeIf { row.linkedInGame }
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
                linkedIn = linkedInCard?.let { card -> { LinkedInCardSlot(card) } },
            )
        },
        calendar = {
            val range = row.progress?.range
            if (range != null) {
                val requested = month ?: YearMonth.from(range.endInclusive)
                ArchiveMonthCalendar(
                    range = range,
                    // The last loaded month and its days, as a pair: the asked month's days land later, and drawing it
                    // before then would show a title with an empty grid (backlog #1551).
                    month = shown?.month ?: requested,
                    days = shown?.days.orEmpty(),
                    around = shown?.around.orEmpty(),
                    requested = requested,
                    picked = past?.date,
                    offline = unreachable,
                    accent = row.card.brand.accent.colors(),
                    compact = layout == PlayLayout.SinglePane,
                    onMonth = calendar.onShowMonth,
                    onToday = calendar.onBackToToday,
                    onPick = { day ->
                        calendar.onPick(day)
                        if (layout == PlayLayout.SinglePane) {
                            scope.launch { if (reduced) scroll.scrollTo(0) else scroll.animateScrollTo(0) }
                        }
                    },
                    modifier = if (layout == PlayLayout.TwoPane) Modifier.fillMaxHeight() else Modifier,
                    // Only a game with an archive has a calendar, and nothing to pick from without one.
                    onRandom = onRandom?.let { random -> { random(game) } },
                )
            }
        },
        onBack = onBack,
        onOpenLesson = { onOpenLesson(game) },
        offline = offline,
        hasLesson = hasLesson,
        scroll = scroll,
        // The board shows the day the day card shows: the picked day, else the card's own latest day.
        board = {
            GamePageRankRow(
                game,
                onOpen = { onOpenRanks(game, past?.date ?: row.cardDay) },
                linkedIn = linkedInCard != null,
                picked = past?.date,
                cardDay = row.cardDay,
            )
        },
        onMakeOne = onMakeOne?.let { make -> { make(game) } },
        onSeeAllStats = onOpenStats?.let { open -> { open(game) } },
    )
}
