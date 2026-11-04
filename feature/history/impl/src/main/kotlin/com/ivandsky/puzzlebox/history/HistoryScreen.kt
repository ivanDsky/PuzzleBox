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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.paging.compose.LazyPagingItems
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * The history browser (A8): the days the app has captured or solved, newest first,
 * paged lazily — per game (fix round 5, defect C): every row carries its GameId and
 * game display name instead of assuming Zip; a solved Queens day lists exactly like a
 * solved Zip day. Phones — and portrait of any width — are single-pane (list, or a
 * day's detail with a back arrow); expanded landscape is list-detail side by side (07).
 *
 * Replay (A4): tapping a day opens its detail; "Replay this day" asks for the day's
 * session blob ([onReplayDay], per (game, day) — replay rides each game's cache seam)
 * and [events] answers: a ready blob goes to the play route through [onReplay]; a
 * missing day or an unreadable payload surfaces its snackbar. The record badge on a row/detail is
 * cached-data only (A8): whether a record exists for the day, never its history of
 * replays. Rows are game-keyed and carry their game's display name (fix round 5,
 * defect C; tango-05): every identity (selection, record lookup, replay) is
 * (game, day), never day alone.
 */
@Composable
fun HistoryScreen(
    layout: HistoryLayout,
    onBack: () -> Unit,
    days: LazyPagingItems<HistoryDayRow>,
    allRows: List<HistoryDayRow>,
    recordsByKey: Map<Pair<GameId, LocalDate>, CompletionRecord>,
    streak: Int,
    events: Flow<HistoryEvent>,
    onReplayDay: (GameId, LocalDate) -> Unit,
    onReplay: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val selectedRow = selectedKey?.let { key -> allRows.firstOrNull { it.key == key } }
    val currentOnReplay by rememberUpdatedState(onReplay)

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is HistoryEvent.ReplayReady -> currentOnReplay(event.session)
                HistoryEvent.ReplayMissing ->
                    launch { snackbarHostState.showSnackbar("That day is no longer saved on this device.") }
                is HistoryEvent.ReplayUnreadable ->
                    launch {
                        snackbarHostState.showSnackbar("This puzzle's data couldn't be read, so it can't be replayed.")
                    }
            }
        }
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
                    streak = streak,
                    capturedCount = allRows.size,
                    recordsByKey = recordsByKey,
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
                            record = recordsByKey[selected.gameId to selected.day],
                            onBackToDayList = null,
                            onReplay = { onReplayDay(selected.gameId, selected.day) },
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
                        streak = streak,
                        capturedCount = allRows.size,
                        recordsByKey = recordsByKey,
                        selectedKey = null,
                        onDayClick = { selectedKey = it.key },
                        onBack = onBack,
                        cardColor = cardColor,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    DayDetailPane(
                        row = selected,
                        record = recordsByKey[selected.gameId to selected.day],
                        onBackToDayList = { selectedKey = null },
                        onReplay = { onReplayDay(selected.gameId, selected.day) },
                        cardColor = cardColor,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Spacing.xl),
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
