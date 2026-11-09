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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.paging.compose.LazyPagingItems
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/**
 * The history browser (spec A18, A28): the All timeline — the streak and stats, a week
 * calendar that opens to the month, then every game's days newest first, paged lazily
 * and grouped by day. A calendar day ([onSelectDay]) narrows the list to that day;
 * [onShowCalendar] pages the calendar and switches week/month. Every row carries its
 * GameId, glyph and display name (fix round 5, defect C).
 * Phones — and portrait of any width — are single-pane (list, or a day's detail with a
 * back arrow); expanded landscape is list-detail side by side (07).
 *
 * Replay (A4, ticket 11): tapping a day opens its detail; "See result" / "Keep going"
 * reports the (game, day) through [onReplay] and the shell opens that day's game slot.
 * Every identity (selection, best lookup, replay) is (game, day), never day alone
 * (tango-05).
 */
@Composable
fun HistoryScreen(
    layout: HistoryLayout,
    onBack: () -> Unit,
    days: LazyPagingItems<HistoryDayRow>,
    state: HistoryUiState,
    onShowCalendar: (day: LocalDate, expanded: Boolean) -> Unit,
    onSelectDay: (LocalDate?) -> Unit,
    onReplay: (GameId, LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedRow = selectedKey?.let { key -> state.rows.firstOrNull { it.key == key } }

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
                    onShowCalendar = onShowCalendar,
                    onSelectDay = onSelectDay,
                    selectedKey = selectedRow?.key,
                    onDayClick = { selectedKey = it.key },
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
                        onShowCalendar = onShowCalendar,
                        onSelectDay = onSelectDay,
                        selectedKey = null,
                        onDayClick = { selectedKey = it.key },
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
    }
}

/** [row]'s game's best row, if it has a numbered solve. */
private fun HistoryUiState.bestOf(row: HistoryDayRow): HistoryDayRow? =
    rows.firstOrNull { it.gameId == row.gameId && it.isBest }

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
