package com.ivandsky.puzzlebox.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.data.model.ThemeMode
import com.ivandsky.puzzlebox.designsystem.AppDialog
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.LargeTopBar
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleBoard
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** One row in the history teaser. Phase 0 renders the day only; scores land in Phase 1. */
data class HistoryDay(
    val isoDay: String,
    val headline: String,
)

/**
 * The real Home screen (A12, A15): Today card, history teaser, account placeholder.
 * Single-pane on compact widths; list-detail on expanded. The theme choice is hoisted
 * to the composition root — this screen only edits it.
 */
@Composable
fun HomeScreen(
    layout: HomeLayout,
    themeMode: ThemeMode,
    onSetThemeMode: (ThemeMode) -> Unit,
    onOpenHistory: () -> Unit = {},
    history: List<HistoryDay>,
    modifier: Modifier = Modifier,
) {
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }
    var selectedDay by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun comingSoon(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    // Cards float ABOVE the pane in both themes (ticket 11): white on cool paper in
    // light, lighter slate on near-black in dark. The M3 container ramp inverts its
    // direction between themes (light descends from white, dark ascends from
    // near-black), so the pane/card pairing flips with the resolved scheme — this
    // also stays correct when the user forces the opposite appearance.
    val scheme = MaterialTheme.colorScheme
    val cardsFloatLighter = scheme.surface.luminance() > scheme.surfaceContainerHighest.luminance()
    val paneColor = if (cardsFloatLighter) scheme.surfaceContainer else scheme.surface
    val cardColor = if (cardsFloatLighter) scheme.surface else scheme.surfaceContainerLow

    Box(modifier.fillMaxSize()) {
        if (layout == HomeLayout.TwoPane) {
            Row(Modifier.fillMaxSize()) {
                ListPane(
                    themeMode = themeMode,
                    onOpenTheme = { showThemeDialog = true },
                    onOpenHistory = onOpenHistory,
                    history = history,
                    selectedDay = selectedDay,
                    onDayClick = { selectedDay = it },
                    selectable = true,
                    onPlayToday = { comingSoon("Zip is coming soon.") },
                    onSignIn = { comingSoon("Google sign-in arrives with leaderboards.") },
                    paneColor = paneColor,
                    cardColor = cardColor,
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight(),
                )
                DetailPane(
                    selected = selectedDay?.let { iso -> history.firstOrNull { it.isoDay == iso } },
                    onPlayToday = { comingSoon("Zip is coming soon.") },
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight(),
                )
            }
        } else {
            ListPane(
                themeMode = themeMode,
                onOpenTheme = { showThemeDialog = true },
                onOpenHistory = onOpenHistory,
                history = history,
                selectedDay = null,
                onDayClick = {},
                selectable = false,
                onPlayToday = { comingSoon("Zip is coming soon.") },
                onSignIn = { comingSoon("Google sign-in arrives with leaderboards.") },
                paneColor = paneColor,
                cardColor = cardColor,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Spacing.xl),
        )
    }

    if (showThemeDialog) {
        ThemeDialog(
            current = themeMode,
            onSelect = onSetThemeMode,
            onDismiss = { showThemeDialog = false },
        )
    }
}

/** The left pane and the whole single-pane screen share one stacked content column. */
@Composable
private fun ListPane(
    themeMode: ThemeMode,
    onOpenTheme: () -> Unit,
    onOpenHistory: () -> Unit,
    history: List<HistoryDay>,
    selectedDay: String?,
    onDayClick: (String) -> Unit,
    selectable: Boolean,
    onPlayToday: () -> Unit,
    onSignIn: () -> Unit,
    paneColor: Color,
    cardColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(paneColor)
            .verticalScroll(rememberScrollState()),
    ) {
        LargeTopBar(
            title = "PuzzleBox",
            subtitle = todayHeadline(),
            actions = {
                IconButton(onClick = onOpenTheme) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Appearance",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
        StaggeredItem(0) {
            TodayCard(
                onPlayToday = onPlayToday,
                cardColor = cardColor,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        }
        StaggeredItem(1) {
            HistorySection(
                history = history,
                selectedDay = selectedDay,
                onDayClick = onDayClick,
                selectable = selectable,
                onOpenHistory = onOpenHistory,
                cardColor = cardColor,
                modifier = Modifier.padding(top = Spacing.xl),
            )
        }
        StaggeredItem(2) {
            AccountCard(
                onSignIn = onSignIn,
                cardColor = cardColor,
                modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.xl, bottom = Spacing.xxl),
            )
        }
    }
}

/** The one filled card: today's game, its tagline, and the primary action. */
@Composable
private fun TodayCard(
    onPlayToday: () -> Unit,
    cardColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home.today_card"),
        shape = MaterialTheme.shapes.large,
        color = cardColor,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = "Daily · Zip",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Zip",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Trace one continuous path through every square. A new puzzle every day.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton(
                text = "Play today",
                onClick = onPlayToday,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.md),
            )
        }
    }
}

@Composable
private fun HistorySection(
    history: List<HistoryDay>,
    selectedDay: String?,
    onDayClick: (String) -> Unit,
    selectable: Boolean,
    onOpenHistory: () -> Unit,
    cardColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Your history",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenHistory) {
                Text(text = "See all", style = MaterialTheme.typography.labelLarge)
            }
        }
        if (history.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = cardColor,
            ) {
                EmptyState(
                    title = "No games yet",
                    message = "Finished puzzles and scores will appear here.",
                )
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = cardColor,
            ) {
                Column(Modifier.padding(Spacing.sm)) {
                    history.forEach { day ->
                        HistoryRow(
                            day = day,
                            selected = selectable && day.isoDay == selectedDay,
                            onClick = if (selectable) {
                                { onDayClick(day.isoDay) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    day: HistoryDay,
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = day.headline,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Daily Zip",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            enabled = true,
            shape = MaterialTheme.shapes.small,
            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        ) { rowContent() }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = Color.Transparent,
        ) { rowContent() }
    }
}

@Composable
private fun AccountCard(
    onSignIn: () -> Unit,
    cardColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
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
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = "Guest player",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Sign in with Google to compete on leaderboards.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SecondaryButton(text = "Sign in", onClick = onSignIn)
        }
    }
}

/** Expanded-width detail pane: today's preview until a history day is selected. */
@Composable
private fun DetailPane(
    selected: HistoryDay?,
    onPlayToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = selected,
        modifier = modifier
            .testTag("home.detail_pane"),
        // Shared axis (A12): arriving content decelerates in with a slight rise,
        // leaving content accelerates out — exit is the faster half.
        transitionSpec = {
            (
                fadeIn(tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingEmphasizedDecelerate)) +
                    slideInHorizontally(tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingEmphasizedDecelerate)) { it / 12 }
                )
                .togetherWith(fadeOut(tween(MotionTokens.DurationShort, easing = MotionTokens.EasingEmphasizedAccelerate)))
        },
        label = "home-detail",
        contentKey = { it?.isoDay },
    ) { day ->
        if (day == null) {
            TodayDetail(onPlayToday = onPlayToday, modifier = Modifier.fillMaxSize())
        } else {
            DayDetail(day = day, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun TodayDetail(
    onPlayToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Spacer(Modifier.weight(1f))
        ZipPreviewBoard(modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth())
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = "Today's Zip",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "A fresh grid every day. Fill the path, check your time, keep your streak.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xl),
            )
        }
        PrimaryButton(text = "Play today", onClick = onPlayToday)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun DayDetail(
    day: HistoryDay,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            text = day.headline,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Daily Zip",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Replays and scores for past days arrive in a future update.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Spacing.xl),
        )
        Spacer(Modifier.weight(1f))
    }
}

/** Decorative Zip board: a traced path with numbered endpoints, no gestures. */
@Composable
private fun ZipPreviewBoard(modifier: Modifier = Modifier) {
    val path = remember {
        // Ticket 11: Zip's rule is that every cell is visited, so the decorative
        // board is a 5x5 Hamiltonian path — row snake on top, then a vertical comb
        // through the bottom three rows so the rhythm doesn't read mechanical.
        listOf(
            0 to 0, 0 to 1, 0 to 2, 0 to 3, 0 to 4,
            1 to 4, 1 to 3, 1 to 2, 1 to 1, 1 to 0,
            2 to 0, 3 to 0, 4 to 0, 4 to 1, 3 to 1,
            2 to 1, 2 to 2, 3 to 2, 4 to 2, 4 to 3,
            3 to 3, 2 to 3, 2 to 4, 3 to 4, 4 to 4,
        )
    }
    val scheme = MaterialTheme.colorScheme
    PuzzleBoard(
        cellsPerSide = 5,
        modifier = modifier.semantics { contentDescription = "Zip board preview" },
        cellContent = { row, col ->
            val index = path.indexOf(row to col)
            if (index >= 0) {
                val from = path.getOrNull(index - 1)
                val to = path.getOrNull(index + 1)
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(1.dp)
                        .background(
                            color = scheme.primary.copy(alpha = 0.12f),
                            shape = MaterialTheme.shapes.extraSmall,
                        ),
                )
                Canvas(Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val strokeWidthPx = size.width * 0.16f
                    listOfNotNull(from, to).forEach { (r, c) ->
                        val target = Offset(
                            center.x + (c - col) * size.width,
                            center.y + (r - row) * size.height,
                        )
                        drawLine(scheme.primary, center, target, strokeWidthPx, StrokeCap.Round)
                    }
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (index == 0 || index == path.lastIndex) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(scheme.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (index == 0) "1" else "${path.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onPrimary,
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(scheme.primary, CircleShape),
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun ThemeDialog(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(
        title = "Appearance",
        onDismissRequest = onDismiss,
        content = {
            Text(
                text = "Choose light, dark, or follow your system.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = current == mode,
                        onClick = { onSelect(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                    ) {
                        Text(
                            when (mode) {
                                ThemeMode.SYSTEM -> "System"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                            },
                        )
                    }
                }
            }
        },
        actions = { SecondaryButton(text = "Done", onClick = onDismiss) },
    )
}

/** First-composition entrance: items rise 12dp and fade in, staggered 60ms apart. */
@Composable
private fun StaggeredItem(
    index: Int,
    content: @Composable () -> Unit,
) {
    var appeared by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(60L * index)
        appeared = true
    }
    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingEmphasizedDecelerate),
        label = "home-entrance-$index",
    )
    Box(
        Modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 12.dp.toPx()
        },
    ) { content() }
}

private fun todayHeadline(): String =
    LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))

/** ISO day ("2026-10-31") to a display headline, with the raw day as fallback. */
internal fun formatDay(isoDay: String): String =
    runCatching { LocalDate.parse(isoDay).format(DateTimeFormatter.ofPattern("MMMM d")) }
        .getOrDefault(isoDay)
