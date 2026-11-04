package com.ivandsky.puzzlebox.history

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
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

@Composable
private fun ListPane(
    days: LazyPagingItems<HistoryDayRow>,
    streak: Int,
    capturedCount: Int,
    recordsByKey: Map<Pair<GameId, LocalDate>, CompletionRecord>,
    selectedKey: String?,
    onDayClick: (HistoryDayRow) -> Unit,
    onBack: () -> Unit,
    cardColor: Color,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("history.list"),
    ) {
        item(key = "top") {
            CompactTopBar(
                title = "History",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
        if (days.loadState.refresh is LoadState.NotLoading && days.itemCount == 0) {
            item(key = "empty") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
                    shape = MaterialTheme.shapes.large,
                    color = cardColor,
                ) {
                    EmptyState(
                        title = "Nothing captured yet",
                        message = "Days you play are saved on this device — replay any of them, even offline.",
                        modifier = Modifier
                            .testTag("history.empty"),
                    )
                }
            }
        } else {
            if (capturedCount > 0) {
                item(key = "streak") {
                    StreakTeaser(
                        streak = streak,
                        captured = capturedCount,
                        cardColor = cardColor,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }
            }
            items(
                count = days.itemCount,
                // Game-keyed identity: two games can share a day — the key never
                // collides (ADR-0004 conventions, tango-05).
                key = days.itemKey { it.key },
                contentType = { "row" },
            ) { index ->
                val row = days[index] ?: return@items
                HistoryRowView(
                    row = row,
                    won = recordsByKey.containsKey(row.gameId to row.day),
                    selected = row.key == selectedKey,
                    onClick = { onDayClick(row) },
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
        }
    }
}

/** Cached-data-only streak teaser (A8): consecutive captured days up to the newest. */
@Composable
private fun StreakTeaser(
    streak: Int,
    captured: Int,
    cardColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("history.streak"),
        shape = MaterialTheme.shapes.large,
        color = cardColor,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.xl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$streak",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = "Day streak",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (captured == 1) "1 day saved on this device" else "$captured days saved on this device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HistoryRowView(
    row: HistoryDayRow,
    won: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("history.row.${row.key}"),
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.headline,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (won) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Record set",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Expanded-width detail placeholder until a day is selected (home's DetailPane voice). */
@Composable
private fun DetailHint(
    cardColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.lg)
            .testTag("history.detail_hint"),
        shape = MaterialTheme.shapes.large,
        color = cardColor,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Select a day",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "See what you captured and replay it whenever you like.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** One captured or solved day: identity, cached record state, and the replay action
 *  when the game supports it (fix round 5, defect C — the pane renders any game's row;
 *  the captured line + replay ride Zip's cache seam and appear only when [entry]
 *  supplies a captured Zip day). */
@Composable
private fun DayDetailPane(
    row: HistoryDayRow,
    record: CompletionRecord?,
    onBackToDayList: (() -> Unit)?,
    onReplay: () -> Unit,
    cardColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("history.detail.${row.gameId.value}.${row.day}"),
    ) {
        CompactTopBar(
            title = row.headline,
            navigationIcon = onBackToDayList?.let {
                {
                    IconButton(onClick = it) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to days",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (row.fetchedAtMs > 0L) {
                Text(
                    text = "Captured ${formatCapturedAt(row.fetchedAtMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RecordCard(
                record = record,
                cardColor = cardColor,
                modifier = Modifier.padding(vertical = Spacing.md),
            )
            PrimaryButton(
                text = "Replay this day",
                onClick = onReplay,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history.replay"),
            )
            Spacer(Modifier.padding(bottom = Spacing.xl))
        }
    }
}

@Composable
private fun RecordCard(
    record: CompletionRecord?,
    cardColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("history.record"),
        shape = MaterialTheme.shapes.large,
        color = cardColor,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.xl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            if (record != null) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        text = "Your record",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${formatScore(record.time)} · ${record.hints} ${if (record.hints == 1) "hint" else "hints"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        text = "Not finished yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Finish it once and your time is locked in here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
