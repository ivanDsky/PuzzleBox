package com.ivandsky.puzzlebox.history

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.paging.compose.LazyPagingItems
import com.ivandsky.puzzlebox.designsystem.AppDialog
import com.ivandsky.puzzlebox.designsystem.AppTextButton
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.PastDay
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * The history browser (spec A18): tabs for All and each game, the tab's stats and a
 * week calendar that opens to the month, then its days newest first, paged lazily and
 * grouped by day. A calendar day ([onSelectDay]) narrows the list to that day;
 * [onShowCalendar] pages the calendar and switches week/month. Every row carries its
 * GameId, glyph and display name (fix round 5, defect C).
 * Phones — and portrait of any width — are single-pane (list, or a day's detail with a
 * back arrow); expanded landscape is list-detail side by side (07).
 *
 * Replay (A4, ticket 11): tapping a day opens its detail; "Play again" / "Keep going"
 * reports the (game, day) through [onReplay] and the shell opens that day's game slot.
 * On a game's tab any archive day can be picked on the calendar; its day header's action
 * reports through [onReplay] too (ticket 09).
 * "Older puzzles" opens any archive game's date picker, its headline the selected day as
 * [pastDay] reads it; opening a day there reports through [onReplay] as well.
 * Every identity (selection, best lookup, replay) is (game, day), never day alone
 * (tango-05). A selection outside the [selectedTab] closes, back to the list.
 */
@Composable
fun HistoryScreen(
    layout: HistoryLayout,
    onBack: () -> Unit,
    days: LazyPagingItems<HistoryDayRow>,
    state: HistoryUiState,
    selectedTab: GameId?,
    onSelectTab: (GameId?) -> Unit,
    onShowCalendar: (day: LocalDate, expanded: Boolean) -> Unit,
    onSelectDay: (LocalDate?) -> Unit,
    onReplay: (GameId, LocalDate) -> Unit,
    pastDay: (GameId, LocalDate) -> Flow<PastDay>,
    modifier: Modifier = Modifier,
) {
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedRow = selectedKey?.let { key -> state.rows.firstOrNull { it.key == key } }
    // The selected tab's rows own the selection: a pick outside the new tab closes.
    val selectTab: (GameId?) -> Unit = { game ->
        if (game != null && selectedRow?.gameId != game) selectedKey = null
        onSelectTab(game)
    }

    // Single-pane (A16, N17): back from a day's detail returns to the day list first;
    // only enabled while a day shows, so the next back leaves History. Predictive, so
    // the system's back preview plays instead of a blocking intercept.
    PredictiveBackHandler(enabled = layout == HistoryLayout.SinglePane && selectedRow != null) { progress ->
        progress.collect {}
        selectedKey = null
    }

    // Cards float ABOVE the pane in both themes (home convention): white on cool paper
    // in light, lighter slate on near-black in dark — the pane/card pairing flips with
    // the resolved scheme so it stays correct when the user forces the other appearance.
    val scheme = MaterialTheme.colorScheme
    val cardsFloatLighter = scheme.surface.luminance() > scheme.surfaceContainerHighest.luminance()
    val paneColor = if (cardsFloatLighter) scheme.surfaceContainer else scheme.surface
    val cardColor = if (cardsFloatLighter) scheme.surface else scheme.surfaceContainerLow

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(paneColor),
    ) {
        if (layout == HistoryLayout.TwoPane) {
            Row(Modifier.fillMaxSize()) {
                ListPane(
                    days = days,
                    state = state,
                    selectedTab = selectedTab,
                    onSelectTab = selectTab,
                    onShowCalendar = onShowCalendar,
                    onSelectDay = onSelectDay,
                    selectedKey = selectedRow?.key,
                    onDayClick = { selectedKey = it.key },
                    onReplay = onReplay,
                    onBack = onBack,
                    cardColor = cardColor,
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight(),
                )
                AnimatedContent(
                    targetState = selectedRow,
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight()
                        .testTag("history.detail_pane"),
                    transitionSpec = { detailTransition() },
                    label = "history-detail",
                    contentKey = { it?.key },
                ) { selected ->
                    if (selected == null) {
                        DetailHint(cardColor = cardColor, modifier = Modifier.fillMaxSize())
                    } else {
                        DayDetailPane(
                            row = selected,
                            gameBest = state.bestOf(selected),
                            onBackToDayList = null,
                            onReplay = { onReplay(selected.gameId, selected.day) },
                            cardColor = cardColor,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        } else {
            AnimatedContent(
                targetState = selectedRow,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { detailTransition() },
                label = "history-detail",
                contentKey = { it?.key },
            ) { selected ->
                if (selected == null) {
                    ListPane(
                        days = days,
                        state = state,
                        selectedTab = selectedTab,
                        onSelectTab = selectTab,
                        onShowCalendar = onShowCalendar,
                        onSelectDay = onSelectDay,
                        selectedKey = null,
                        onDayClick = { selectedKey = it.key },
                        onReplay = onReplay,
                        onBack = onBack,
                        cardColor = cardColor,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    DayDetailPane(
                        row = selected,
                        gameBest = state.bestOf(selected),
                        onBackToDayList = { selectedKey = null },
                        onReplay = { onReplay(selected.gameId, selected.day) },
                        cardColor = cardColor,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        // Over the list only: a single-pane day detail owns the whole screen.
        if (state.archiveGames.isNotEmpty() && (layout == HistoryLayout.TwoPane || selectedRow == null)) {
            OlderPuzzles(
                games = state.archiveGames,
                onOpen = onReplay,
                pastDay = pastDay,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Spacing.lg),
            )
        }
    }
}

/** [row]'s game's best row in the tab, if it has a numbered solve. */
private fun HistoryUiState.bestOf(row: HistoryDayRow): HistoryDayRow? =
    rows.firstOrNull { it.gameId == row.gameId && it.isBest }

/**
 * The "Older puzzles" entry (A12): one archive game opens its date picker directly,
 * several ask for the game first. The picked (game, day) goes out as a replay; the
 * picker's headline reads the selected day through [pastDay].
 */
@Composable
private fun OlderPuzzles(
    games: List<ArchiveGame>,
    onOpen: (GameId, LocalDate) -> Unit,
    pastDay: (GameId, LocalDate) -> Flow<PastDay>,
    modifier: Modifier = Modifier,
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    var pickingId by rememberSaveable { mutableStateOf<String?>(null) }
    val picking = games.firstOrNull { it.gameId.value == pickingId }
    SecondaryButton(
        text = "Older puzzles",
        onClick = { if (games.size == 1) pickingId = games.single().gameId.value else choosing = true },
        modifier = modifier.testTag("history.older"),
    )
    if (choosing) {
        AppDialog(title = "Older puzzles", onDismissRequest = { choosing = false }) {
            games.forEach { game ->
                AppTextButton(
                    text = game.name,
                    onClick = {
                        choosing = false
                        pickingId = game.gameId.value
                    },
                    modifier = Modifier.testTag("history.older.game.${game.gameId.value}"),
                )
            }
        }
    }
    picking?.let { game ->
        OlderPuzzlesPicker(
            range = game.range,
            gameName = game.name,
            pastDay = { day -> pastDay(game.gameId, day) },
            onOpen = { day ->
                pickingId = null
                onOpen(game.gameId, day)
            },
            onDismiss = { pickingId = null },
        )
    }
}

/** Shared-axis pane swap (A12): arriving detail decelerates in, leaving accelerates out. */
private fun AnimatedContentTransitionScope<HistoryDayRow?>.detailTransition(): ContentTransform =
    (
        fadeIn(tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingEmphasizedDecelerate)) +
            slideInHorizontally(
                tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingEmphasizedDecelerate),
            ) { it / 12 }
        ).togetherWith(
        fadeOut(tween(MotionTokens.DurationShort, easing = MotionTokens.EasingEmphasizedAccelerate)),
    )
